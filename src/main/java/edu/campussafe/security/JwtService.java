package edu.campussafe.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtService {
  private final SecretKey key;
  public JwtService(@Value("${app.jwt.secret}") String secret) { key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
  public String create(Map<String,Object> user) { return Jwts.builder().subject(String.valueOf(user.get("_id"))).claim("role", user.get("role")).issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(604800))).signWith(key).compact(); }
  public String userId(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}


