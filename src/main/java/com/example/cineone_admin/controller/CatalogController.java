package com.example.cineone_admin.controller;

import com.example.cineone_admin.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalog;

    @GetMapping("/movies")
    public String movies(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String status,
                         @RequestParam(required = false) Long genreId, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("items", catalog.movies(q, status, genreId, page));
        model.addAttribute("genres", catalog.genres()); model.addAttribute("genreId", genreId);
        model.addAttribute("q", q); model.addAttribute("status", status);
        return "movies/list";
    }
    @GetMapping("/movies/{id}")
    public String movie(@PathVariable Long id, Model model) {
        model.addAttribute("item", catalog.movie(id)); return "movies/detail";
    }
    @GetMapping("/cinemas")
    public String cinemas(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String status,
                          @RequestParam(required = false) Long providerId, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("items", catalog.cinemas(q, providerId, status, page));
        model.addAttribute("providers", catalog.providers()); model.addAttribute("providerId", providerId);
        model.addAttribute("q", q); model.addAttribute("status", status);
        return "cinemas/list";
    }
    @GetMapping("/cinemas/{id}")
    public String cinema(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("cinema", catalog.cinema(id)); model.addAttribute("items", catalog.rooms(id, page));
        return "cinemas/detail";
    }
    @GetMapping("/cinemas/{cinemaId}/rooms/{roomId}")
    public String room(@PathVariable Long cinemaId, @PathVariable Long roomId, Model model) {
        model.addAttribute("detail", catalog.room(cinemaId, roomId)); return "rooms/detail";
    }
    @GetMapping("/showtimes")
    public String showtimes(@RequestParam(defaultValue = "") String cinema, @RequestParam(defaultValue = "") String movie,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("items", catalog.showtimes(cinema, movie, date, status, page));
        model.addAttribute("cinema", cinema); model.addAttribute("movie", movie);
        model.addAttribute("date", date); model.addAttribute("status", status);
        return "showtimes/list";
    }
    @GetMapping("/fnb")
    public String fnb(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String cinema,
                      @RequestParam(required = false) Long cinemaId, @RequestParam(defaultValue = "") String status,
                      @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("items", catalog.fnb(q, cinema, cinemaId, status, page));
        model.addAttribute("q", q); model.addAttribute("cinema", cinema); model.addAttribute("cinemaId", cinemaId);
        model.addAttribute("selectedCinema", cinemaId == null ? null : catalog.cinema(cinemaId));
        model.addAttribute("status", status); return "fnb/list";
    }
}
