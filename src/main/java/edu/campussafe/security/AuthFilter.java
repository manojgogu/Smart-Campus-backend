package edu.campussafe.security;

import edu.campussafe.repository.DocumentStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Map;

@Component
public class AuthFilter extends OncePerRequestFilter {
  private final JwtService jwt; private final DocumentStore db;
  public AuthFilter(JwtService jwt, DocumentStore db) { this.jwt=jwt; this.db=db; }

  @Override protected boolean shouldNotFilter(HttpServletRequest r) {
    return "OPTIONS".equalsIgnoreCase(r.getMethod())
        || !r.getRequestURI().startsWith("/api/")
        || r.getRequestURI().equals("/api/health")
        || r.getRequestURI().startsWith("/api/auth/login")
        || r.getRequestURI().startsWith("/api/auth/register");
  }

  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
    if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
      chain.doFilter(req, res);
      return;
    }
    try {
      String h = req.getHeader("Authorization");
      if (h == null || !h.startsWith("Bearer ")) throw new RuntimeException();
      Map<String,Object> u = db.byId(jwt.userId(h.substring(7)), "users");
      if (u == null || Boolean.FALSE.equals(u.get("isActive"))) throw new RuntimeException();
      req.setAttribute("user", u);
      chain.doFilter(req, res);
    } catch(Exception e) {
      res.setStatus(401);
      res.setContentType("application/json");
      res.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
  }
}
