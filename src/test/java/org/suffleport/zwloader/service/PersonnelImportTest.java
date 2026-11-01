package org.suffleport.zwloader.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.dto.ImportReport;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.PositionRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Импорт сотрудников: проверяем, что предварительный просмотр честно считает
 * ошибки и дубли, а применение не пропускает файл с ошибками.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonnelImportTest {

    private static final String[] HEADERS = {
            "Фамилия", "Имя", "Отчество", "Дата рождения", "Телефон", "Должность"
    };

    @Autowired PersonnelImportService importService;
    @Autowired PersonnelRepository personnelRepository;
    @Autowired PositionRepository positionRepository;

    @BeforeEach
    void setUp() {
        if (positionRepository.findByName("Инженер") == null) {
            positionRepository.save(new Position("Инженер", 1));
        }
    }

    @Test
    void previewSeparatesValidRowsFromBrokenOnes() throws Exception {
        personnelRepository.save(new Personnel(
                "Петров", "Пётр", "Петрович", LocalDate.of(1980, 1, 1), null, "+79001112233"));

        byte[] file = workbook(
                new String[]{"Иванов", "Иван", "Иванович", "15.04.1990", "+79001234567", "Инженер"},
                new String[]{"Ошибкин", "Ошиб", "", "15.04.1990", "12345", "Инженер"},
                new String[]{"Петров", "Пётр", "Петрович", "01.01.1980", "+79001112233", ""});

        ImportReport report = importService.preview(new ByteArrayInputStream(file));

        assertEquals(3, report.total());
        assertEquals(1, report.ok());
        assertEquals(1, report.errors());
        assertEquals(1, report.skipped());
        assertFalse(report.canApply());
    }

    @Test
    void applyStopsWhenFileContainsErrors() throws Exception {
        byte[] file = workbook(
                new String[]{"Иванов", "Иван", "Иванович", "15.04.1990", "+79001234567", "Инженер"},
                new String[]{"Кривой", "Телефон", "", "15.04.1990", "8-800", "Инженер"});

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.apply(new ByteArrayInputStream(file)));
        assertTrue(ex.getMessage().contains("импорт не выполнен"));
    }

    @Test
    void applyCreatesPersonnelFromCleanFile() throws Exception {
        byte[] file = workbook(
                new String[]{"Иванов", "Иван", "Иванович", "15.04.1990", "+79001234567", "Инженер"},
                new String[]{"Сидорова", "Анна", "", "02.02.1995", "8 (900) 222-33-44", "Инженер"});

        ImportReport report = importService.apply(new ByteArrayInputStream(file));

        assertEquals(2, report.ok());
        assertEquals(0, report.errors());
        assertTrue(report.canApply());

        assertEquals(1, personnelRepository.findByLastName("Иванов").size());
        // Телефон нормализуется к виду +7XXXXXXXXXX
        assertEquals("+79002223344", personnelRepository.findByLastName("Сидорова").get(0).getPhone());
    }

    @Test
    void unknownPositionIsReportedAsError() throws Exception {
        byte[] file = workbook(
                new String[]{"Неизвестный", "Сотрудник", "", "15.04.1990", "+79001234567", "Бармен"});

        ImportReport report = importService.preview(new ByteArrayInputStream(file));

        assertEquals(1, report.errors());
        assertTrue(report.rows().get(0).messages().stream()
                .anyMatch(message -> message.contains("Должность не найдена")));
    }

    @Test
    void duplicateInsideFileIsSkippedOnce() throws Exception {
        byte[] file = workbook(
                new String[]{"Козлов", "Игорь", "", "10.10.1985", "+79003334455", "Инженер"},
                new String[]{"Козлов", "Игорь", "", "10.10.1985", "+79003334455", "Инженер"});

        ImportReport report = importService.preview(new ByteArrayInputStream(file));

        assertEquals(2, report.total());
        assertEquals(1, report.ok());
        assertEquals(1, report.skipped());
        assertTrue(report.rows().get(1).messages().get(0).contains("строка 2"));
    }

    @Test
    void applyRejectsFileWithoutRowsToImport() throws Exception {
        personnelRepository.save(new Personnel(
                "Петров", "Пётр", "Петрович", LocalDate.of(1980, 1, 1), null, "+79001112233"));

        byte[] file = workbook(
                new String[]{"Петров", "Пётр", "Петрович", "01.01.1980", "+79001112233", "Инженер"});

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.apply(new ByteArrayInputStream(file)));
        assertTrue(ex.getMessage().contains("Импортировать нечего"));
    }

    private byte[] workbook(String[]... dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Сотрудники");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    row.createCell(c).setCellValue(dataRows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
