package net.unit8.raoh.examples.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;

import java.util.Map;

/**
 * Turns a request body that {@code JsonDecoders.readTree} cannot read into a 400 response.
 *
 * <p>The controllers take the body as text and read it themselves, so Spring no longer rejects
 * malformed JSON on their behalf. {@code readTree} throws for text that is not one JSON value and
 * for an object that has a member name twice; neither is a validation issue, since no decoder has
 * seen the input yet, so the response carries the reader's message instead of a list of issues.
 * Only the reading exceptions are handled, so a failure to write a response is not reported as the
 * client's fault.
 */
@RestControllerAdvice
public class MalformedJsonHandler {

    /**
     * Answers a request whose body could not be read as JSON.
     *
     * @param e the exception the reader threw
     * @return 400 with the reader's message
     */
    @ExceptionHandler({StreamReadException.class, StreamConstraintsException.class})
    public ResponseEntity<Map<String, String>> malformed(JacksonException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getOriginalMessage()));
    }
}
