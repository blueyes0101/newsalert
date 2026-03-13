package com.newsalert.alert.resource;

import com.newsalert.alert.dto.AuthRequest;
import com.newsalert.alert.dto.TokenResponse;
import com.newsalert.alert.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Authentication endpoints – no JWT required.
 *
 * POST /api/auth/register  – create account, receive token
 * POST /api/auth/login     – verify credentials, receive token
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @POST
    @Path("/register")
    @Transactional
    public Response register(@Valid AuthRequest req) {
        if (User.existsByEmail(req.email)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Email already registered\"}")
                    .build();
        }

        User user = new User();
        user.email = req.email.toLowerCase().trim();
        user.passwordHash = BcryptUtil.bcryptHash(req.password);
        user.persist();

        LOG.infof("New user registered: %s (id=%d)", user.email, user.id);

        return Response.status(Response.Status.CREATED)
                .entity(new TokenResponse(buildToken(user)))
                .build();
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @POST
    @Path("/login")
    public Response login(@Valid AuthRequest req) {
        User user = User.findByEmail(req.email.toLowerCase().trim());

        if (user == null || !BcryptUtil.matches(req.password, user.passwordHash)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Invalid email or password\"}")
                    .build();
        }

        LOG.debugf("User logged in: %s", user.email);

        return Response.ok(new TokenResponse(buildToken(user))).build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildToken(User user) {
        return Jwt.issuer(issuer)
                .upn(user.email)
                .groups("user")
                .claim("userId", user.id)
                .expiresIn(TOKEN_TTL)
                .sign();
    }
}
