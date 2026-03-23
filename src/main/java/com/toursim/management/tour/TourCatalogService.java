package com.toursim.management.tour;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toursim.management.tour.dto.TourAdminRequest;

import jakarta.annotation.PostConstruct;

@Service
public class TourCatalogService {

    private static final List<String> REGION_ORDER = List.of(
        "Europe",
        "Asia",
        "Africa",
        "North America",
        "South America",
        "Middle East",
        "Oceania",
        "More Destinations"
    );

    private static final Map<String, String> COUNTRY_TO_REGION = Map.ofEntries(
        Map.entry("Argentina", "South America"),
        Map.entry("Australia", "Oceania"),
        Map.entry("Canada", "North America"),
        Map.entry("Egypt", "Africa"),
        Map.entry("France", "Europe"),
        Map.entry("Iceland", "Europe"),
        Map.entry("Indonesia", "Asia"),
        Map.entry("Japan", "Asia"),
        Map.entry("Jordan", "Middle East"),
        Map.entry("Kenya", "Africa"),
        Map.entry("Morocco", "Africa"),
        Map.entry("Nepal", "Asia"),
        Map.entry("New Zealand", "Oceania"),
        Map.entry("Norway", "Europe"),
        Map.entry("Peru", "South America"),
        Map.entry("South Africa", "Africa"),
        Map.entry("Switzerland", "Europe"),
        Map.entry("Tanzania", "Africa"),
        Map.entry("Turkey", "Europe"),
        Map.entry("Vietnam", "Asia")
    );

    private static final RowMapper<Tour> TOUR_ROW_MAPPER = (rs, rowNum) -> {
        Tour tour = new Tour();
        tour.setId(rs.getString("id"));
        tour.setTitle(rs.getString("title"));
        tour.setDestination(rs.getString("destination"));
        tour.setCountry(rs.getString("country"));
        tour.setDuration(rs.getString("duration"));
        tour.setPrice(rs.getBigDecimal("price"));
        tour.setOriginalPrice(rs.getBigDecimal("original_price"));
        tour.setRating(rs.getBigDecimal("rating").doubleValue());
        tour.setReviews(rs.getInt("reviews"));
        tour.setImage(rs.getString("image"));
        tour.setCategory(rs.getString("category"));
        tour.setDescription(rs.getString("description"));
        tour.setDifficulty(rs.getString("difficulty"));
        tour.setMaxGroupSize(rs.getInt("max_group_size"));
        return tour;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TourCatalogService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Transactional
    void syncSeedCatalog() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/tours.json");
        try (InputStream inputStream = resource.getInputStream()) {
            List<Tour> seedTours = objectMapper.readValue(inputStream, new TypeReference<List<Tour>>() {
            });
            for (Tour tour : seedTours) {
                Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tours WHERE id = :id",
                    new MapSqlParameterSource("id", tour.getId()),
                    Integer.class
                );
                if (existing == null || existing == 0) {
                    upsert(tourToRequest(tour));
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Tour> findAll() {
        return loadTours(
            "SELECT id, category, country, description, destination, difficulty, duration, image, max_group_size, original_price, price, rating, reviews, title FROM tours ORDER BY title",
            new MapSqlParameterSource()
        );
    }

    @Transactional(readOnly = true)
    public Optional<Tour> findById(String id) {
        List<Tour> tours = loadTours(
            "SELECT id, category, country, description, destination, difficulty, duration, image, max_group_size, original_price, price, rating, reviews, title FROM tours WHERE id = :id",
            new MapSqlParameterSource("id", id)
        );
        return tours.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<Tour> featuredTours(int limit) {
        return findAll().stream()
            .sorted(Comparator.comparing(Tour::getRating).reversed().thenComparing(Tour::getReviews, Comparator.reverseOrder()))
            .limit(limit)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Tour> relatedTours(Tour currentTour, int limit) {
        return findAll().stream()
            .filter(tour -> !tour.getId().equals(currentTour.getId()))
            .filter(tour -> tour.getCategory().equalsIgnoreCase(currentTour.getCategory()))
            .limit(limit)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> categories() {
        List<Tour> tours = findAll();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("All", (long) tours.size());
        tours.forEach(tour -> counts.merge(tour.getCategory(), 1L, Long::sum));
        return counts.entrySet().stream()
            .map(entry -> new CategorySummary(entry.getKey(), entry.getValue()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DestinationRegion> destinationRegions() {
        Map<String, List<String>> groupedCountries = new LinkedHashMap<>();
        REGION_ORDER.forEach(region -> groupedCountries.put(region, new ArrayList<>()));

        findAll().stream()
            .map(Tour::getCountry)
            .distinct()
            .sorted()
            .forEach(country -> groupedCountries
                .computeIfAbsent(COUNTRY_TO_REGION.getOrDefault(country, "More Destinations"), key -> new ArrayList<>())
                .add(country));

        return groupedCountries.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> new DestinationRegion(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
    }

    @Transactional
    public Tour upsert(TourAdminRequest request) {
        String id = request.id() == null || request.id().isBlank()
            ? generateId(request.title())
            : request.id().trim();

        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tours WHERE id = :id",
            new MapSqlParameterSource("id", id),
            Integer.class
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("category", request.category().trim())
            .addValue("country", request.country().trim())
            .addValue("description", request.description().trim())
            .addValue("destination", request.destination().trim())
            .addValue("difficulty", request.difficulty().trim())
            .addValue("duration", request.duration().trim())
            .addValue("image", request.image().trim())
            .addValue("maxGroupSize", request.maxGroupSize())
            .addValue("originalPrice", request.originalPrice())
            .addValue("price", request.price())
            .addValue("rating", Optional.ofNullable(request.rating()).orElse(BigDecimal.ZERO))
            .addValue("reviews", request.reviews())
            .addValue("title", request.title().trim());

        if (existing != null && existing > 0) {
            jdbcTemplate.update("""
                UPDATE tours
                SET category = :category,
                    country = :country,
                    description = :description,
                    destination = :destination,
                    difficulty = :difficulty,
                    duration = :duration,
                    image = :image,
                    max_group_size = :maxGroupSize,
                    original_price = :originalPrice,
                    price = :price,
                    rating = :rating,
                    reviews = :reviews,
                    title = :title
                WHERE id = :id
                """, params);
        } else {
            jdbcTemplate.update("""
                INSERT INTO tours (id, category, country, description, destination, difficulty, duration, image, max_group_size, original_price, price, rating, reviews, title)
                VALUES (:id, :category, :country, :description, :destination, :difficulty, :duration, :image, :maxGroupSize, :originalPrice, :price, :rating, :reviews, :title)
                """, params);
        }

        replaceChildRows(id, "tour_highlights", "highlight", request.highlights());
        replaceChildRows(id, "tour_included", "included_item", request.included());
        replaceDateRows(id, request.startDates());

        return findById(id).orElseThrow();
    }

    @Transactional
    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM tour_highlights WHERE tour_id = :id", new MapSqlParameterSource("id", id));
        jdbcTemplate.update("DELETE FROM tour_included WHERE tour_id = :id", new MapSqlParameterSource("id", id));
        jdbcTemplate.update("DELETE FROM tour_start_dates WHERE tour_id = :id", new MapSqlParameterSource("id", id));
        jdbcTemplate.update("DELETE FROM tours WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private List<Tour> loadTours(String sql, MapSqlParameterSource params) {
        List<Tour> tours = jdbcTemplate.query(sql, params, TOUR_ROW_MAPPER);
        attachCollections(tours);
        return tours;
    }

    private void attachCollections(List<Tour> tours) {
        if (tours.isEmpty()) {
            return;
        }

        Map<String, Tour> byId = tours.stream()
            .collect(Collectors.toMap(Tour::getId, tour -> tour, (left, right) -> left, LinkedHashMap::new));
        MapSqlParameterSource params = new MapSqlParameterSource("ids", new ArrayList<>(byId.keySet()));

        jdbcTemplate.query(
            "SELECT tour_id, highlight FROM tour_highlights WHERE tour_id IN (:ids) ORDER BY display_order",
            params,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                byId.get(rs.getString("tour_id")).getHighlights().add(rs.getString("highlight"))
        );

        jdbcTemplate.query(
            "SELECT tour_id, included_item FROM tour_included WHERE tour_id IN (:ids) ORDER BY display_order",
            params,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                byId.get(rs.getString("tour_id")).getIncluded().add(rs.getString("included_item"))
        );

        jdbcTemplate.query(
            "SELECT tour_id, start_date FROM tour_start_dates WHERE tour_id IN (:ids) ORDER BY display_order",
            params,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                byId.get(rs.getString("tour_id")).getStartDates().add(rs.getDate("start_date").toLocalDate())
        );
    }

    private void replaceChildRows(String tourId, String tableName, String valueColumn, List<String> values) {
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE tour_id = :id", new MapSqlParameterSource("id", tourId));
        for (int index = 0; index < values.size(); index++) {
            jdbcTemplate.update(
                "INSERT INTO " + tableName + " (tour_id, " + valueColumn + ", display_order) VALUES (:id, :value, :displayOrder)",
                new MapSqlParameterSource()
                    .addValue("id", tourId)
                    .addValue("value", values.get(index).trim())
                    .addValue("displayOrder", index)
            );
        }
    }

    private void replaceDateRows(String tourId, List<java.time.LocalDate> startDates) {
        jdbcTemplate.update("DELETE FROM tour_start_dates WHERE tour_id = :id", new MapSqlParameterSource("id", tourId));
        for (int index = 0; index < startDates.size(); index++) {
            jdbcTemplate.update(
                "INSERT INTO tour_start_dates (tour_id, start_date, display_order) VALUES (:id, :startDate, :displayOrder)",
                new MapSqlParameterSource()
                    .addValue("id", tourId)
                    .addValue("startDate", Date.valueOf(startDates.get(index)))
                    .addValue("displayOrder", index)
            );
        }
    }

    private TourAdminRequest tourToRequest(Tour tour) {
        return new TourAdminRequest(
            tour.getId(),
            tour.getTitle(),
            tour.getDestination(),
            tour.getCountry(),
            tour.getDuration(),
            tour.getPrice(),
            tour.getOriginalPrice(),
            BigDecimal.valueOf(tour.getRating()),
            tour.getReviews(),
            tour.getImage(),
            tour.getCategory(),
            tour.getDescription(),
            tour.getDifficulty(),
            tour.getMaxGroupSize(),
            tour.getHighlights(),
            tour.getIncluded(),
            tour.getStartDates()
        );
    }

    private String generateId(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "")
            + "-" + System.currentTimeMillis();
    }
}
