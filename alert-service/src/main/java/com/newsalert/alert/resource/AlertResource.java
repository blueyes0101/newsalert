package com.newsalert.alert.resource;

import com.newsalert.alert.dto.AlertDTO;
import com.newsalert.alert.dto.CreateAlertRequest;
import com.newsalert.alert.entity.Alert;
import com.newsalert.alert.entity.User;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Alert management endpoints – all require a valid JWT with role "user".
 *
 * GET    /api/alerts           – list own alerts
 * POST   /api/alerts           – create alert
 * DELETE /api/alerts/{id}      – delete alert
 * PUT    /api/alerts/{id}/toggle – enable / disable alert
 */
@Path("/api/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class AlertResource {

    private static final Logger LOG = Logger.getLogger(AlertResource.class);

    @Inject
    JsonWebToken jwt;

    // ── GET /api/alerts ───────────────────────────────────────────────────────

    @GET
    @Transactional
    public List<AlertDTO> list() {
        User user = resolveUser();
        return Alert.findActiveByUser(user.id)
                .stream()
                .map(AlertDTO::from)
                .toList();
    }

    // ── POST /api/alerts ──────────────────────────────────────────────────────

    @POST
    @Transactional
    public Response create(@Valid CreateAlertRequest req) {
        User user = resolveUser();

        Alert alert = new Alert();
        alert.keyword = req.keyword.trim();
        alert.user = user;
        alert.persist();

        LOG.infof("Alert created: keyword='%s' user='%s' id=%d",
                alert.keyword, user.email, alert.id);

        return Response.status(Response.Status.CREATED)
                .entity(AlertDTO.from(alert))
                .build();
    }

    // ── DELETE /api/alerts/{id} ───────────────────────────────────────────────

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Alert alert = findOwnAlert(id);
        if (alert == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Alert not found\"}")
                    .build();
        }

        alert.delete();
        LOG.infof("Alert deleted: id=%d user='%s'", id, jwt.getName());
        return Response.noContent().build();
    }

    // ── PUT /api/alerts/{id}/toggle ───────────────────────────────────────────

    @PUT
    @Path("/{id}/toggle")
    @Transactional
    public Response toggle(@PathParam("id") Long id) {
        Alert alert = findOwnAlert(id);
        if (alert == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Alert not found\"}")
                    .build();
        }

        alert.active = !alert.active;
        LOG.infof("Alert toggled: id=%d active=%b user='%s'",
                id, alert.active, jwt.getName());

        return Response.ok(AlertDTO.from(alert)).build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves the User entity from the JWT's upn (email) claim. */
    private User resolveUser() {
        User user = User.findByEmail(jwt.getName());
        if (user == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"error\":\"User not found\"}")
                            .build());
        }
        return user;
    }

    /**
     * Finds an alert by id that belongs to the current user.
     * Returns null when not found or not owned by this user.
     */
    private Alert findOwnAlert(Long id) {
        Alert alert = Alert.findById(id);
        if (alert == null) return null;

        User user = resolveUser();
        if (!alert.user.id.equals(user.id)) return null;

        return alert;
    }
}
