package edu.campussafe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
public class CampusSafeApplication {
  public static void main(String[] args) { SpringApplication.run(CampusSafeApplication.class, args); }
  @Bean BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean com.fasterxml.jackson.databind.Module mongoObjectIdModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(ObjectId.class, new JsonSerializer<ObjectId>() {
      @Override public void serialize(ObjectId value, JsonGenerator generator, SerializerProvider serializers) throws java.io.IOException { generator.writeString(value.toHexString()); }
    });
    return module;
  }
}

@Component
class JwtService {
  private final SecretKey key;
  JwtService(@Value("${app.jwt.secret}") String secret) { key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
  String create(Map<String,Object> user) { return Jwts.builder().subject(user.get("_id").toString()).claim("role", user.get("role")).issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(604800))).signWith(key).compact(); }
  String userId(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}

@Component
class AuthFilter extends OncePerRequestFilter {
  private final JwtService jwt; private final MongoTemplate db;
  AuthFilter(JwtService jwt, MongoTemplate db) { this.jwt=jwt; this.db=db; }
  @Override protected boolean shouldNotFilter(HttpServletRequest r) { return !r.getRequestURI().startsWith("/api/") || r.getRequestURI().equals("/api/health") || r.getRequestURI().startsWith("/api/auth/login") || r.getRequestURI().startsWith("/api/auth/register"); }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
    try { String h=req.getHeader("Authorization"); if(h==null||!h.startsWith("Bearer ")) throw new RuntimeException(); Map<String,Object> u=db.findById(new ObjectId(jwt.userId(h.substring(7))), Map.class, "users"); if(u==null||Boolean.FALSE.equals(u.get("isActive"))) throw new RuntimeException(); req.setAttribute("user",u); chain.doFilter(req,res); }
    catch(Exception e) { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Unauthorized\"}"); }
  }
}

@RestController
@RequestMapping("/api")
class ApiController {
  private final MongoTemplate db; private final BCryptPasswordEncoder encoder; private final JwtService jwt;
  @Value("${app.upload-dir}") private String uploadDir;
  ApiController(MongoTemplate db, BCryptPasswordEncoder encoder, JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
  private Map<String,Object> user(jakarta.servlet.http.HttpServletRequest r) { return (Map<String,Object>)r.getAttribute("user"); }
  private Map<String,Object> out(Object... kv) { Map<String,Object> m=new LinkedHashMap<>(); for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]); return m; }
  private Map<String,Object> safe(Map<String,Object> u) { Map<String,Object> x=new LinkedHashMap<>(u); x.remove("password"); return x; }
  private void role(Map<String,Object> u, String... roles) { if(Arrays.stream(roles).noneMatch(x->x.equals(u.get("role")))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Forbidden"); }
  private Map<String,Object> findIncident(String id) { Map<String,Object> x=db.findOne(Query.query(Criteria.where("incidentId").is(id)),Map.class,"incidents"); if(x==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found"); return x; }
  private void ownerOrStaff(Map<String,Object> inc, Map<String,Object> u) { if(!Objects.equals(String.valueOf(inc.get("reporter")),String.valueOf(u.get("_id")))) role(u,"security","admin"); }
  private Map<String,Object> notify(String title,String message,Object recipient,Object incident) { Map<String,Object> n=out("title",title,"message",message,"role",recipient==null?"security":"all","read",false,"createdAt",new Date()); if(recipient!=null)n.put("user",recipient); if(incident!=null)n.put("incident",incident); Map<String,Object> saved=db.insert(n,"notifications"); if(recipient==null){Map<String,Object> admin=new LinkedHashMap<>(n);admin.remove("_id");admin.put("role","admin");db.insert(admin,"notifications");} return saved; }
  @GetMapping("/health") Map<String,Object> health(){return out("ok",true,"service","campussafe","time",Instant.now().toString());}

  @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
  Map<String,Object> register(@RequestBody Map<String,Object> b) {
    String name=String.valueOf(b.getOrDefault("name","")), email=String.valueOf(b.getOrDefault("email","" )).toLowerCase(), pass=String.valueOf(b.getOrDefault("password","")), role=String.valueOf(b.getOrDefault("role","student"));
    if(name.trim().length()<2||!email.contains("@")||pass.length()<8)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name, valid email, and password (min 8 chars) required"); if(!List.of("student","faculty","security").contains(role))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin cannot self-register"); if(db.exists(Query.query(Criteria.where("email").is(email)),"users"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
    Map<String,Object> u=out("name",name.trim(),"email",email,"password",encoder.encode(pass),"role",role,"phone",b.getOrDefault("phone",""),"isActive",true,"createdAt",new Date(),"updatedAt",new Date()); u=db.insert(u,"users"); return out("token",jwt.create(u),"user",safe(u));
  }
  @PostMapping("/auth/login") Map<String,Object> login(@RequestBody Map<String,Object> b){String email=String.valueOf(b.getOrDefault("email","")).toLowerCase(); Map<String,Object> u=db.findOne(Query.query(Criteria.where("email").is(email)),Map.class,"users"); if(u==null||!encoder.matches(String.valueOf(b.getOrDefault("password","")),String.valueOf(u.get("password"))))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"); if(Boolean.FALSE.equals(u.get("isActive")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account deactivated. Contact admin."); return out("token",jwt.create(u),"user",safe(u)); }
  @GetMapping("/auth/me") Map<String,Object> me(jakarta.servlet.http.HttpServletRequest r){return out("user",safe(user(r)));}
  @PatchMapping("/auth/profile") Map<String,Object> profile(jakarta.servlet.http.HttpServletRequest r,@RequestBody Map<String,Object>b){Map<String,Object>u=user(r); if(b.containsKey("name"))u.put("name",b.get("name"));if(b.containsKey("phone"))u.put("phone",b.get("phone"));db.save(u,"users");return out("user",safe(u));}

  @PostMapping(value="/incidents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
  Map<String,Object> createIncident(jakarta.servlet.http.HttpServletRequest r,@RequestParam Map<String,String>b,@RequestPart(required=false) MultipartFile image)throws Exception {
    Map<String,Object>u=user(r); role(u,"student","faculty"); String desc=b.getOrDefault("description","").trim(),loc=b.getOrDefault("locationName","").trim(),cat=b.getOrDefault("category",""); if(desc.length()<10||loc.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Description min 10 chars and location required");
    String id="CS-"+new java.text.SimpleDateFormat("yyyyMMdd").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,5).toUpperCase(); Map<String,Object> coords=null; if(!b.getOrDefault("lat","").isBlank()&&!b.getOrDefault("lng","").isBlank())coords=out("lat",Double.parseDouble(b.get("lat")),"lng",Double.parseDouble(b.get("lng")));
    String imageUrl="";if(image!=null&&!image.isEmpty()){if(image.getSize()>5242880)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image must be 5MB or smaller");Files.createDirectories(Path.of(uploadDir));String fn=UUID.randomUUID()+"-"+Path.of(Objects.requireNonNull(image.getOriginalFilename())).getFileName();Files.copy(image.getInputStream(),Path.of(uploadDir,fn));imageUrl="/uploads/"+fn;}
    Map<String,Object> hist=out("status","Reported","note","Incident reported","by",u.get("_id"),"at",new Date()); Map<String,Object> inc=out("incidentId",id,"reporter",u.get("_id"),"category",cat,"priority",b.getOrDefault("priority","Medium"),"description",desc,"locationName",loc,"coordinates",coords,"imageUrl",imageUrl,"contactNumber",b.getOrDefault("contactNumber",""),"assignedTo","","status","Reported","history",new ArrayList<>(List.of(hist)),"createdAt",new Date(),"updatedAt",new Date());inc=db.insert(inc,"incidents");inc.put("reporter",out("_id",u.get("_id"),"name",u.get("name"),"email",u.get("email"),"phone",u.get("phone"))); notify("New "+inc.get("priority")+" incident",id+" · "+cat+" at "+loc,null,inc.get("_id"));return out("incident",inc);
  }
  @GetMapping("/incidents/my") Map<String,Object> mine(jakarta.servlet.http.HttpServletRequest r,@RequestParam(defaultValue="20")int limit){Map<String,Object>u=user(r);return out("incidents",db.find(Query.query(Criteria.where("reporter").is(u.get("_id"))).with(Sort.by(Sort.Direction.DESC,"createdAt")).limit(Math.min(limit,100)),Map.class,"incidents"));}
  @GetMapping("/incidents/my/stats") Map<String,Object> myStats(jakarta.servlet.http.HttpServletRequest r){Object uid=user(r).get("_id");long total=db.count(Query.query(Criteria.where("reporter").is(uid)),"incidents"),active=db.count(Query.query(Criteria.where("reporter").is(uid).and("status").ne("Resolved")),"incidents");return out("stats",out("total",total,"active",active,"resolved",total-active));}
  @GetMapping("/incidents/track/{id}") Map<String,Object> track(@PathVariable String id,jakarta.servlet.http.HttpServletRequest r){Map<String,Object>i=findIncident(id);ownerOrStaff(i,user(r));return out("incident",i);}
  @GetMapping("/incidents") Map<String,Object> list(@RequestParam Map<String,String> p,jakarta.servlet.http.HttpServletRequest r){role(user(r),"security","admin");Query q=new Query(); if(p.containsKey("priority"))q.addCriteria(Criteria.where("priority").is(p.get("priority")));if(p.containsKey("status"))q.addCriteria(Criteria.where("status").is(p.get("status")));if("true".equals(p.get("active")))q.addCriteria(Criteria.where("status").ne("Resolved"));int lim=Math.min(Integer.parseInt(p.getOrDefault("limit","50")),200),page=Math.max(1,Integer.parseInt(p.getOrDefault("page","1")));q.with(Sort.by(Sort.Direction.DESC,"createdAt")).skip((long)(page-1)*lim).limit(lim);return out("incidents",db.find(q,Map.class,"incidents"),"total",db.count(new Query(),"incidents"));}
  @GetMapping("/incidents/{id}") Map<String,Object> incident(@PathVariable String id,jakarta.servlet.http.HttpServletRequest r){Map<String,Object>i=findIncident(id);ownerOrStaff(i,user(r));return out("incident",i);}
  @PatchMapping("/incidents/{id}/status") Map<String,Object> status(@PathVariable String id,@RequestBody Map<String,Object>b,jakarta.servlet.http.HttpServletRequest r){Map<String,Object>u=user(r);role(u,"security","admin");Map<String,Object>i=findIncident(id);String s=String.valueOf(b.get("status"));if(!List.of("Reported","Acknowledged","Assigned","In Progress","Resolved").contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");i.put("status",s);if(b.containsKey("assignedTo"))i.put("assignedTo",b.get("assignedTo"));if("Acknowledged".equals(s))i.putIfAbsent("acknowledgedAt",new Date());if("Assigned".equals(s))i.putIfAbsent("assignedAt",new Date());if("Resolved".equals(s))i.put("resolvedAt",new Date());List<Map<String,Object>>h=(List<Map<String,Object>>)i.get("history");if(h==null)h=new ArrayList<>();h.add(out("status",s,"note",b.getOrDefault("note",""),"by",u.get("_id"),"at",new Date()));i.put("history",h);db.save(i,"incidents");notify("Incident "+i.get("incidentId")+": "+s,String.valueOf(b.getOrDefault("note","Status changed to "+s)),i.get("reporter"),i.get("_id"));return out("incident",i);}
  @GetMapping("/notifications") Map<String,Object> notifications(jakarta.servlet.http.HttpServletRequest r,@RequestParam(defaultValue="20")int limit){Map<String,Object>u=user(r);Query q=new Query(new Criteria().orOperator(Criteria.where("user").is(u.get("_id")),new Criteria().andOperator(Criteria.where("user").exists(false),new Criteria().orOperator(Criteria.where("role").is(u.get("role")),Criteria.where("role").is("all"))))).with(Sort.by(Sort.Direction.DESC,"createdAt")).limit(Math.min(limit,50));return out("notifications",db.find(q,Map.class,"notifications"));}
  @PatchMapping("/notifications/read") Map<String,Object> read(jakarta.servlet.http.HttpServletRequest r){db.updateMulti(Query.query(Criteria.where("user").is(user(r).get("_id"))),new Update().set("read",true),"notifications");return out("ok",true);}
  @GetMapping("/teams") Map<String,Object> teams(jakarta.servlet.http.HttpServletRequest r){role(user(r),"security","admin");return out("teams",db.findAll(Map.class,"responseteams"));}
  @PostMapping("/teams") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createTeam(@RequestBody Map<String,Object>b,jakarta.servlet.http.HttpServletRequest r){role(user(r),"admin");return out("team",db.insert(b,"responseteams"));}
  @GetMapping("/admin/teams") Map<String,Object> adminTeams(jakarta.servlet.http.HttpServletRequest r){return teams(r);}
  @PostMapping("/admin/teams") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> adminCreateTeam(@RequestBody Map<String,Object>b,jakarta.servlet.http.HttpServletRequest r){return createTeam(b,r);}
  @GetMapping("/admin/locations") Map<String,Object> locations(jakarta.servlet.http.HttpServletRequest r){return out("locations",db.findAll(Map.class,"campuslocations"));}
  @PostMapping("/admin/locations") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createLocation(@RequestBody Map<String,Object>b,jakarta.servlet.http.HttpServletRequest r){role(user(r),"admin");return out("location",db.insert(b,"campuslocations"));}
  @GetMapping("/admin/users") Map<String,Object> users(@RequestParam(defaultValue="50")int limit,jakarta.servlet.http.HttpServletRequest r){role(user(r),"admin");List<Map> rows=db.find(new Query().with(Sort.by(Sort.Direction.DESC,"createdAt")).limit(Math.min(limit,200)),Map.class,"users");return out("users",rows.stream().map(this::safe).toList(),"total",db.count(new Query(),"users"));}
  @PatchMapping("/admin/users/{id}/toggle") Map<String,Object> toggle(@PathVariable String id,jakarta.servlet.http.HttpServletRequest r){role(user(r),"admin");if(id.equals(String.valueOf(user(r).get("_id"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot deactivate yourself");Map<String,Object>u=db.findById(new ObjectId(id),Map.class,"users");if(u==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");u.put("isActive",!Boolean.FALSE.equals(u.get("isActive")));db.save(u,"users");return out("user",safe(u));}
  @GetMapping("/admin/stats") Map<String,Object> stats(jakarta.servlet.http.HttpServletRequest r){role(user(r),"admin");List<Map> incidents=db.findAll(Map.class,"incidents");Map<String,Long> cats=new LinkedHashMap<>(),statuses=new LinkedHashMap<>(),locs=new LinkedHashMap<>();for(Map i:incidents){cats.merge(String.valueOf(i.get("category")),1L,Long::sum);statuses.merge(String.valueOf(i.get("status")),1L,Long::sum);locs.merge(String.valueOf(i.get("locationName")),1L,Long::sum);}long active=incidents.stream().filter(i->!"Resolved".equals(i.get("status"))).count();return out("counts",out("total",incidents.size(),"active",active,"resolved",incidents.size()-active,"users",db.count(new Query(),"users")),"byCategory",cats.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"byStatus",statuses.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"byLocation",locs.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"overTime",List.of(),"avgResponseMinutes",null);}
}

@Component
class WebConfig implements WebMvcConfigurer {
  @Value("${app.client-url}") String client;
  @Override public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOriginPatterns(client,"http://localhost:*","http://127.0.0.1:*").allowedMethods("GET","POST","PATCH","OPTIONS").allowedHeaders("*");}
  @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");}
}

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class) org.springframework.http.ResponseEntity<Map<String,String>> status(ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Request failed")));}
  @ExceptionHandler(Exception.class) org.springframework.http.ResponseEntity<Map<String,String>> error(Exception e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Request failed")));}
}
