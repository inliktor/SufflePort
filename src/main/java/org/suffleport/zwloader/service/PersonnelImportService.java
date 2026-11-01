package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.dto.ImportReport;
import org.suffleport.zwloader.dto.ImportRowResult;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.PositionRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Импорт сотрудников из файла Excel.
 *
 * Работает в два шага. Сначала preview — читаем файл, проверяем каждую строку
 * и показываем отчёт, ничего не записывая в базу. Затем apply — тот же файл
 * разбирается повторно и, если ошибок нет, данные сохраняются.
 *
 * Такое разделение удобно на практике: кадровик видит проблемы до того,
 * как в справочнике появятся половина корректных записей и половина мусора.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonnelImportService {

    /** Колонки файла: фамилия, имя, отчество, дата рождения, телефон, должность. */
    private static final String[] HEADERS = {
            "Фамилия", "Имя", "Отчество", "Дата рождения", "Телефон", "Должность"
    };

    private static final int COL_LAST_NAME = 0;
    private static final int COL_FIRST_NAME = 1;
    private static final int COL_MIDDLE_NAME = 2;
    private static final int COL_BIRTH_DATE = 3;
    private static final int COL_PHONE = 4;
    private static final int COL_POSITION = 5;

    private static final int MAX_ROWS = 2000;
    private static final DateTimeFormatter DATE_DOTS = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PersonnelRepository personnelRepository;
    private final PositionRepository positionRepository;

    /**
     * Проверка без записи в БД.
     */
    public ImportReport preview(InputStream inputStream) {
        return toReport(parse(inputStream));
    }

    /**
     * Применение файла. Сохраняем только когда ошибок нет совсем:
     * частично применённый импорт труднее разобрать, чем полностью отклонённый.
     */
    @Transactional
    public ImportReport apply(InputStream inputStream) {
        ParseResult parsed = parse(inputStream);
        ImportReport report = toReport(parsed);
        if (report.errors() > 0) {
            throw new IllegalStateException("Файл содержит ошибки, импорт не выполнен. Исправьте строки "
                    + "с ошибками и повторите загрузку");
        }
        if (report.ok() == 0) {
            throw new IllegalStateException("Импортировать нечего: все строки либо пустые, "
                    + "либо уже есть в справочнике");
        }

        int created = 0;
        for (ParsedRow row : parsed.rows) {
            if (!row.errors.isEmpty() || row.skipReason != null) {
                continue;
            }
            Position position = row.positionId != null
                    ? positionRepository.findById(row.positionId).orElse(null)
                    : null;
            Personnel person = new Personnel(
                    row.lastName, row.firstName, row.middleName, row.birthDate, position, row.phone);
            personnelRepository.save(person);
            created++;
        }

        log.info("Импорт сотрудников: создано {} записей из {}", created, report.total());
        return new ImportReport(report.total(), created, report.skipped(), 0, true, report.rows());
    }

    /**
     * Пустой шаблон с заголовками и одной строкой-примером.
     */
    public byte[] buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Сотрудники");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }

            Row example = sheet.createRow(1);
            example.createCell(COL_LAST_NAME).setCellValue("Иванов");
            example.createCell(COL_FIRST_NAME).setCellValue("Иван");
            example.createCell(COL_MIDDLE_NAME).setCellValue("Иванович");
            example.createCell(COL_BIRTH_DATE).setCellValue("15.04.1990");
            example.createCell(COL_PHONE).setCellValue("+79001234567");
            example.createCell(COL_POSITION).setCellValue("Инженер");

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сформировать шаблон файла", ex);
        }
    }

    // ------------------------------------------------------------------ разбор

    private ParseResult parse(InputStream inputStream) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("ru-RU"));

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("В файле нет ни одного листа");
            }
            checkHeaders(sheet, formatter);

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            // Ключ ФИО+телефон → номер строки, где он встретился впервые
            Map<String, Integer> seenInFile = new HashMap<>();
            int lastRow = sheet.getLastRowNum();
            for (int index = 1; index <= lastRow; index++) {
                if (rows.size() >= MAX_ROWS) {
                    throw new IllegalArgumentException("Файл слишком большой: не больше "
                            + MAX_ROWS + " строк за один импорт");
                }
                Row row = sheet.getRow(index);
                if (row == null || isEmptyRow(row, formatter, evaluator)) {
                    continue;
                }
                rows.add(parseRow(row, index + 1, formatter, evaluator, seenInFile));
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось прочитать файл Excel: " + ex.getMessage());
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("В файле нет данных: заполнены только заголовки");
        }
        return new ParseResult(rows);
    }

    private void checkHeaders(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalArgumentException("Первая строка должна содержать заголовки столбцов");
        }
        String first = formatter.formatCellValue(header.getCell(COL_LAST_NAME)).trim();
        String second = formatter.formatCellValue(header.getCell(COL_FIRST_NAME)).trim();
        if (!HEADERS[COL_LAST_NAME].equalsIgnoreCase(first) || !HEADERS[COL_FIRST_NAME].equalsIgnoreCase(second)) {
            throw new IllegalArgumentException(
                    "Ожидались столбцы «Фамилия» и «Имя». Скачайте актуальный шаблон и перенесите данные в него");
        }
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int i = 0; i <= COL_POSITION; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !formatter.formatCellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private ParsedRow parseRow(Row row, int humanRowNumber, DataFormatter formatter,
                              FormulaEvaluator evaluator, Map<String, Integer> seenInFile) {
        ParsedRow parsed = new ParsedRow(humanRowNumber);
        parsed.lastName = text(row, COL_LAST_NAME, formatter, evaluator);
        parsed.firstName = text(row, COL_FIRST_NAME, formatter, evaluator);
        parsed.middleName = text(row, COL_MIDDLE_NAME, formatter, evaluator);

        try {
            parsed.lastName = ValidationRules.requireHumanName(parsed.lastName, "Фамилия", 100);
        } catch (IllegalArgumentException ex) {
            parsed.errors.add(ex.getMessage());
        }
        try {
            parsed.firstName = ValidationRules.requireHumanName(parsed.firstName, "Имя", 100);
        } catch (IllegalArgumentException ex) {
            parsed.errors.add(ex.getMessage());
        }
        try {
            parsed.middleName = ValidationRules.optionalHumanName(parsed.middleName, "Отчество", 100);
        } catch (IllegalArgumentException ex) {
            parsed.errors.add(ex.getMessage());
        }

        // Дата рождения: поддерживаем и настоящую дату Excel, и текст
        String rawDate = text(row, COL_BIRTH_DATE, formatter, evaluator);
        parsed.birthDate = parseDate(row, rawDate, parsed.errors);

        // Телефон
        String rawPhone = text(row, COL_PHONE, formatter, evaluator);
        if (!rawPhone.isBlank()) {
            try {
                parsed.phone = ValidationRules.normalizePhone(rawPhone);
            } catch (IllegalArgumentException ex) {
                parsed.errors.add("Телефон: " + ex.getMessage());
            }
        }

        // Должность
        String rawPosition = text(row, COL_POSITION, formatter, evaluator);
        if (!rawPosition.isBlank()) {
            Position position = positionRepository.findByName(rawPosition);
            if (position == null) {
                parsed.errors.add("Должность не найдена в справочнике: " + rawPosition);
            } else {
                parsed.positionId = position.getId();
            }
        }

        // Дубли внутри справочника
        if (parsed.errors.isEmpty()) {
            String fullName = joinName(parsed.lastName, parsed.firstName, parsed.middleName);
            for (Personnel existing : personnelRepository.findByLastName(parsed.lastName)) {
                boolean sameName = existing.getFullName() != null
                        && existing.getFullName().equalsIgnoreCase(fullName);
                boolean samePhone = parsed.phone != null && parsed.phone.equals(existing.getPhone());
                if (sameName && samePhone) {
                    parsed.skipReason = "Сотрудник с таким ФИО и телефоном уже есть в справочнике";
                    break;
                }
            }

            // Повтор внутри самого файла раньше не отсекался:
            // проверка ходила только в БД, а вставки начинались после разбора всего файла.
            if (parsed.skipReason == null) {
                String key = (fullName + "|" + (parsed.phone == null ? "" : parsed.phone))
                        .toLowerCase(Locale.ROOT);
                Integer firstRow = seenInFile.putIfAbsent(key, humanRowNumber);
                if (firstRow != null) {
                    parsed.skipReason = "Такая же строка уже есть в файле (строка " + firstRow + ")";
                }
            }
        }

        return parsed;
    }

    private LocalDate parseDate(Row row, String rawDate, List<String> errors) {
        if (rawDate.isBlank()) {
            return null;
        }
        Cell cell = row.getCell(COL_BIRTH_DATE);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE_DOTS);
        } catch (DateTimeParseException ignored) {
            // ниже пробуем ISO
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE_ISO);
        } catch (DateTimeParseException ex) {
            errors.add("Дата рождения должна быть в формате дд.мм.гггг: " + rawDate);
            return null;
        }
    }

    private String text(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private String joinName(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName).append(' ');
        if (firstName != null) sb.append(firstName).append(' ');
        if (middleName != null) sb.append(middleName);
        return sb.toString().trim();
    }

    private ImportReport toReport(ParseResult parsed) {
        List<ImportRowResult> rows = new ArrayList<>(parsed.rows.size());
        int ok = 0;
        int skipped = 0;
        int errors = 0;

        for (ParsedRow row : parsed.rows) {
            String fullName = joinName(row.lastName, row.firstName, row.middleName);
            if (!row.errors.isEmpty()) {
                errors++;
                rows.add(new ImportRowResult(row.rowNumber, fullName, "ERROR", List.copyOf(row.errors)));
            } else if (row.skipReason != null) {
                skipped++;
                rows.add(new ImportRowResult(row.rowNumber, fullName, "SKIP", List.of(row.skipReason)));
            } else {
                ok++;
                rows.add(new ImportRowResult(row.rowNumber, fullName, "OK", List.of()));
            }
        }

        return new ImportReport(parsed.rows.size(), ok, skipped, errors, errors == 0 && ok > 0, rows);
    }

    private static final class ParseResult {
        private final List<ParsedRow> rows;

        private ParseResult(List<ParsedRow> rows) {
            this.rows = rows;
        }
    }

    private static final class ParsedRow {
        private final int rowNumber;
        private final List<String> errors = new ArrayList<>();
        private String lastName;
        private String firstName;
        private String middleName;
        private LocalDate birthDate;
        private String phone;
        private UUID positionId;
        private String skipReason;

        private ParsedRow(int rowNumber) {
            this.rowNumber = rowNumber;
        }
    }
}
