package io.quarkiverse.langchain4j.sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/movies")
public class MovieResource {

    @Inject
    MovieRecommendationService recommendationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/by-title/{title}")
    public List<Movie> searchByTitle(String title) {
      return Movie.searchByTitleLike(title);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/similar/{id}")
    public List<Movie> searchSimilar(Long id) {
      Movie m = Movie.findById(id);
      return recommendationService.searchSimilarMovies(m.overview);
    }
}
