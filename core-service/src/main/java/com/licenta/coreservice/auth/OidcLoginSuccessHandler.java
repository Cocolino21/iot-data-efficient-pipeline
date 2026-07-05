package com.licenta.coreservice.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OidcLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final String cookieName;
    private final String frontendBaseUrl;

    public OidcLoginSuccessHandler(UserService userService,
                                   JwtService jwtService,
                                   @Value("${app.jwt.cookie-name}") String cookieName,
                                   @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException {
        OAuth2User oauth = (OAuth2User) auth.getPrincipal();
        String email = oauth.getAttribute("email");
        String name = oauth.getAttribute("name");
        String providerId = oauth.getAttribute("sub");

        CurrentUser user = userService.upsertFromOidc("google", providerId, email, name);
        String jwt = jwtService.issue(user.id(), user.email(), user.name());

        Cookie cookie = new Cookie(cookieName, jwt);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) jwtService.ttlSeconds());
        cookie.setSecure(req.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        res.addCookie(cookie);

        // The OAuth dance needed a session for the `state` parameter; drop it now
        // that the JWT cookie carries the identity. Clearing the SecurityContextHolder
        // is what makes the invalidate stick — without it, Spring's end-of-chain
        // auto-save sees a populated context and creates a brand-new session.
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();

        String target = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl + "dashboard"
                : frontendBaseUrl + "/dashboard";
        getRedirectStrategy().sendRedirect(req, res, target);
    }
}
