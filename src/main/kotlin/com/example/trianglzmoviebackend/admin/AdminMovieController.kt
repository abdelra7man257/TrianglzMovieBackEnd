package com.example.trianglzmoviebackend.admin

import com.example.trianglzmoviebackend.movie.MovieNotFoundException
import com.example.trianglzmoviebackend.movie.MovieService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Server-rendered admin portal: browse, search and edit movies. There is no create or delete. */
@Controller
@RequestMapping("/admin/movies")
class AdminMovieController(private val movieService: MovieService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "1") page: Int,
        model: Model,
    ): String {
        val searchText = query.trim()
        model.addAttribute("moviesPage", movieService.listForAdmin(searchText, page, PAGE_SIZE))
        model.addAttribute("query", searchText)
        return "admin/movies"
    }

    @GetMapping("/{id}/edit")
    fun showEditForm(@PathVariable id: Long, model: Model): String {
        val movie = movieService.get(id)
        model.addAttribute("movieId", id)
        model.addAttribute("form", AdminMovieForm.from(movie))
        return "admin/edit"
    }

    @PostMapping("/{id}")
    fun saveEdit(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: AdminMovieForm,
        bindingResult: BindingResult,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (form.genres.size > AdminMovieForm.MAX_GENRES) {
            bindingResult.rejectValue("genresText", "genres.tooMany", "At most ${AdminMovieForm.MAX_GENRES} genres")
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("movieId", id)
            return "admin/edit"
        }
        val updated = movieService.updateMovie(id, form.toUpdateRequest())
        redirectAttributes.addFlashAttribute("successMessage", "Saved \"${updated.title}\".")
        return "redirect:/admin/movies"
    }

    @ExceptionHandler(MovieNotFoundException::class)
    fun movieNotFound(e: MovieNotFoundException, redirectAttributes: RedirectAttributes): String {
        redirectAttributes.addFlashAttribute("errorMessage", e.message)
        return "redirect:/admin/movies"
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
