package com.toursim.management.web;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.toursim.management.tour.TourCatalogService;

@Controller
public class SitemapController {

    private final TourCatalogService tourCatalogService;
    private final String baseUrl;

    public SitemapController(
        TourCatalogService tourCatalogService,
        @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.tourCatalogService = tourCatalogService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + "/" : baseUrl + "/";

    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String today = LocalDate.now().toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static pages
        for (String path : new String[]{
            "", "tours", "destinations", "about", "contact"
        }) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(baseUrl).append(path).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>").append(path.isEmpty() ? "daily" : "weekly").append("</changefreq>\n");
            xml.append("    <priority>").append(path.isEmpty() ? "1.0" : "0.8").append("</priority>\n");
            xml.append("  </url>\n");
        }

        // Tour detail pages
        tourCatalogService.findAll().forEach(tour -> {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(baseUrl).append("tours/").append(tour.getId()).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>weekly</changefreq>\n");
            xml.append("    <priority>0.7</priority>\n");
            xml.append("  </url>\n");
        });

        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return """
            User-agent: *
            Allow: /
            Allow: /tours
            Allow: /tours/
            Allow: /destinations
            Allow: /about
            Allow: /contact
            Allow: /css/
            Allow: /js/

            Disallow: /api/
            Disallow: /dashboard
            Disallow: /login
            Disallow: /register
            Disallow: /forgot-password
            Disallow: /reset-password
            Disallow: /profile
            Disallow: /admin
            Disallow: /error

            Sitemap: %ssitemap.xml
            """.formatted(baseUrl);
    }
}
