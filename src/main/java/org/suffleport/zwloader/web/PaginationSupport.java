package org.suffleport.zwloader.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class PaginationSupport {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PaginationSupport() {
    }

    static boolean isPaged(Integer page, Integer size) {
        return page != null || size != null;
    }

    static Pageable buildPageable(Integer page,
                                  Integer size,
                                  String sortBy,
                                  String sortDir,
                                  String defaultSortBy,
                                  String... allowedSortFields) {
        int safePage = page != null ? page : DEFAULT_PAGE;
        int safeSize = size != null ? size : DEFAULT_SIZE;

        if (safePage < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (safeSize < 1 || safeSize > MAX_SIZE) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до " + MAX_SIZE);
        }

        String safeSortBy = (sortBy == null || sortBy.isBlank()) ? defaultSortBy : sortBy.trim();
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedSortFields));
        allowed.add(defaultSortBy);
        if (!allowed.contains(safeSortBy)) {
            throw new IllegalArgumentException("Недопустимое поле сортировки: " + safeSortBy);
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortDir != null && !sortDir.isBlank()) {
            try {
                direction = Sort.Direction.fromString(sortDir.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Направление сортировки должно быть ASC или DESC");
            }
        }

        return PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
    }
}