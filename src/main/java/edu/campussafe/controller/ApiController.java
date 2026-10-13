package edu.campussafe.controller;

import edu.campussafe.repository.DocumentStore;
import edu.campussafe.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final DocumentStore db; private final BCryptPasswordEncoder encoder; private final JwtService jwt;
  @Value("${app.upload-dir}") private String uploadDir;
  public ApiController(DocumentStore db, BCryptPasswordEncoder encoder, JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
  private Map<String,Object> user(HttpServletRequest r) { return (Map<String,Object>)r.getAttribute("user"); }
  private Map<String,Object> out(Object... kv) { Map<String,Object> m=new LinkedHashMap<>(); for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]); return m; }
  private Map<String,Object> safe(Map<String,Object> u) { Map<String,Object> x=new LinkedHashMap<>(u); x.remove("password"); return x; }
  private void role(Map<String,Object> u, String... roles) { if(Arrays.stream(roles).noneMatch(x->x.equals(u.get("role")))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Forbidden"); }
  private Map<String,Object> findIncident(String id) { Map<String,Object> x=db.first("incidents","incidentId",id); if(x==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found"); return x; }
  private Map<String,Object> presentIncident(Map<String,Object> incident) { Map<String,Object> result=new LinkedHashMap<>(incident); Object reporter=incident.get("reporter"); String id=reporter instanceof Map<?,?> m?String.valueOf(m.get("_id")):String.valueOf(reporter); Map<String,Object> record=db.byId(id,"users"); if(record!=null)result.put("reporter",out("_id",record.get("_id"),"name",record.get("name"),"email",record.get("email"),"phone",record.get("phone"))); return result; }
  private void ownerOrStaff(Map<String,Object> inc, Map<String,Object> u) { Object reporter=inc.get("reporter"); String id=reporter instanceof Map<?,?> m?String.valueOf(m.get("_id")):String.valueOf(reporter); if(!Objects.equals(id,String.valueOf(u.get("_id")))) role(u,"security","admin"); }
  private Map<String,Object> notify(String title,String message,Object recipient,Object incident) { Map<String,Object> n=out("title",title,"message",message,"role",recipient==null?"security":"all","read",false,"createdAt",new Date()); if(recipient!=null)n.put("user",recipient); if(incident!=null)n.put("incident",incident); Map<String,Object> saved=db.insert(n,"notifications"); if(recipient==null){Map<String,Object> admin=new LinkedHashMap<>(n);admin.remove("_id");admin.put("role","admin");db.insert(admin,"notifications");} return saved; }
  @GetMapping("/health") Map<String,Object> health(){return out("ok",true,"service","campussafe","time",Instant.now().toString());}

  @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
  Map<String,Object> register(@RequestBody Map<String,Object> b) {
    String name=String.valueOf(b.getOrDefault("name","")), email=String.valueOf(b.getOrDefault("email","")).toLowerCase(), pass=String.valueOf(b.getOrDefault("password","")), role=String.valueOf(b.getOrDefault("role","student"));
    if(name.trim().length()<2||!email.contains("@")||pass.length()<8)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name, valid email, and password (min 8 chars) required"); if(!List.of("student","faculty","security").contains(role))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin cannot self-register"); if(db.exists("users","email",email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
    Map<String,Object> u=out("name",name.trim(),"email",email,"password",encoder.encode(pass),"role",role,"phone",b.getOrDefault("phone",""),"isActive",true,"createdAt",new Date(),"updatedAt",new Date()); u=db.insert(u,"users"); return out("token",jwt.create(u),"user",safe(u));
  }
  @PostMapping("/auth/login") Map<String,Object> login(@RequestBody Map<String,Object> b){String email=String.valueOf(b.getOrDefault("email","")).toLowerCase(); Map<String,Object> u=db.first("users","email",email); if(u==null||!encoder.matches(String.valueOf(b.getOrDefault("password","")),String.valueOf(u.get("password"))))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"); if(Boolean.FALSE.equals(u.get("isActive")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account deactivated. Contact admin."); return out("token",jwt.create(u),"user",safe(u)); }
  @GetMapping("/auth/me") Map<String,Object> me(HttpServletRequest r){return out("user",safe(user(r)));}
  @PatchMapping("/auth/profile") Map<String,Object> profile(HttpServletRequest r,@RequestBody Map<String,Object>b){Map<String,Object>u=user(r); if(b.containsKey("name"))u.put("name",b.get("name"));if(b.containsKey("phone"))u.put("phone",b.get("phone"));db.save(u,"users");return out("user",safe(u));}

  @PostMapping(value="/incidents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
  Map<String,Object> createIncident(HttpServletRequest r,@RequestParam Map<String,String>b,@RequestPart(required=false) MultipartFile image)throws Exception {
    Map<String,Object>u=user(r); role(u,"student","faculty"); String desc=b.getOrDefault("description","").trim(),loc=b.getOrDefault("locationName","").trim(),cat=b.getOrDefault("category",""); if(desc.length()<10||loc.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Description min 10 chars and location required");
    String id="CS-"+new java.text.SimpleDateFormat("yyyyMMdd").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,5).toUpperCase(); Map<String,Object> coords=null; if(!b.getOrDefault("lat","").isBlank()&&!b.getOrDefault("lng","").isBlank())coords=out("lat",Double.parseDouble(b.get("lat")),"lng",Double.parseDouble(b.get("lng")));
    String imageUrl="";if(image!=null&&!image.isEmpty()){if(image.getSize()>5242880)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image must be 5MB or smaller");Files.createDirectories(Path.of(uploadDir));String fn=UUID.randomUUID()+"-"+Path.of(Objects.requireNonNull(image.getOriginalFilename())).getFileName();Files.copy(image.getInputStream(),Path.of(uploadDir,fn));imageUrl="/uploads/"+fn;}
    Map<String,Object> hist=out("status","Reported","note","Incident reported","by",u.get("_id"),"at",new Date()); Map<String,Object> inc=out("incidentId",id,"reporter",u.get("_id"),"category",cat,"priority",b.getOrDefault("priority","Medium"),"description",desc,"locationName",loc,"coordinates",coords,"imageUrl",imageUrl,"contactNumber",b.getOrDefault("contactNumber",""),"assignedTo","","status","Reported","history",new ArrayList<>(List.of(hist)),"createdAt",new Date(),"updatedAt",new Date());inc=db.insert(inc,"incidents");inc.put("reporter",out("_id",u.get("_id"),"name",u.get("name"),"email",u.get("email"),"phone",u.get("phone"))); notify("New "+inc.get("priority")+" incident",id+" Â· "+cat+" at "+loc,null,inc.get("_id"));return out("incident",inc);
  }
  @GetMapping("/incidents/my") Map<String,Object> mine(HttpServletRequest r,@RequestParam(defaultValue="20")int limit){Map<String,Object>u=user(r);return out("incidents",db.where("incidents","reporter",u.get("_id"),"createdAt",Math.min(limit,100),0).stream().map(this::presentIncident).toList());}
  @GetMapping("/incidents/my/stats") Map<String,Object> myStats(HttpServletRequest r){Object uid=user(r).get("_id");long total=db.count("incidents","reporter",uid),active=db.countNotEqual("incidents","reporter",uid,"status","Resolved");return out("stats",out("total",total,"active",active,"resolved",total-active));}
  @GetMapping("/incidents/track/{id}") Map<String,Object> track(@PathVariable String id,HttpServletRequest r){Map<String,Object>i=findIncident(id);ownerOrStaff(i,user(r));return out("incident",i);}
  @GetMapping("/incidents") Map<String,Object> list(@RequestParam Map<String,String> p,HttpServletRequest r){role(user(r),"security","admin");int lim=Math.min(Integer.parseInt(p.getOrDefault("limit","50")),200),page=Math.max(1,Integer.parseInt(p.getOrDefault("page","1")));return out("incidents",db.incidents(p.get("priority"),p.get("status"),"true".equals(p.get("active")),lim,(page-1)*lim).stream().map(this::presentIncident).toList(),"total",db.count("incidents"));}
  @GetMapping("/incidents/{id}") Map<String,Object> incident(@PathVariable String id,HttpServletRequest r){Map<String,Object>i=findIncident(id);ownerOrStaff(i,user(r));return out("incident",presentIncident(i));}
  @PatchMapping("/incidents/{id}/status") Map<String,Object> status(@PathVariable String id,@RequestBody Map<String,Object>b,HttpServletRequest r){Map<String,Object>u=user(r);role(u,"security","admin");Map<String,Object>i=findIncident(id);String s=String.valueOf(b.get("status"));if(!List.of("Reported","Acknowledged","Assigned","In Progress","Resolved").contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");i.put("status",s);if(b.containsKey("assignedTo"))i.put("assignedTo",b.get("assignedTo"));if("Acknowledged".equals(s))i.putIfAbsent("acknowledgedAt",new Date());if("Assigned".equals(s))i.putIfAbsent("assignedAt",new Date());if("Resolved".equals(s))i.put("resolvedAt",new Date());List<Map<String,Object>>h=(List<Map<String,Object>>)i.get("history");if(h==null)h=new ArrayList<>();h.add(out("status",s,"note",b.getOrDefault("note",""),"by",u.get("_id"),"at",new Date()));i.put("history",h);db.save(i,"incidents");notify("Incident "+i.get("incidentId")+": "+s,String.valueOf(b.getOrDefault("note","Status changed to "+s)),i.get("reporter"),i.get("_id"));return out("incident",presentIncident(i));}
  @GetMapping("/notifications") Map<String,Object> notifications(HttpServletRequest r,@RequestParam(defaultValue="20")int limit){return out("notifications",db.notifications(user(r),Math.min(limit,50)));}
  @PatchMapping("/notifications/read") Map<String,Object> read(HttpServletRequest r){db.markRead(user(r).get("_id"));return out("ok",true);}
  @GetMapping("/teams") Map<String,Object> teams(HttpServletRequest r){role(user(r),"security","admin");return out("teams",db.all("responseteams"));}
  @PostMapping("/teams") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createTeam(@RequestBody Map<String,Object>b,HttpServletRequest r){role(user(r),"admin");return out("team",db.insert(b,"responseteams"));}
  @GetMapping("/admin/teams") Map<String,Object> adminTeams(HttpServletRequest r){return teams(r);}
  @PostMapping("/admin/teams") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> adminCreateTeam(@RequestBody Map<String,Object>b,HttpServletRequest r){return createTeam(b,r);}
  @GetMapping("/admin/locations") Map<String,Object> locations(HttpServletRequest r){role(user(r),"admin");return out("locations",db.all("campuslocations"));}
  @PostMapping("/admin/locations") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createLocation(@RequestBody Map<String,Object>b,HttpServletRequest r){role(user(r),"admin");return out("location",db.insert(b,"campuslocations"));}
  @GetMapping("/admin/users") Map<String,Object> users(@RequestParam(defaultValue="50")int limit,HttpServletRequest r){role(user(r),"admin");List<Map<String,Object>> rows=db.all("users").stream().limit(Math.min(limit,200)).toList();return out("users",rows.stream().map(this::safe).toList(),"total",db.count("users"));}
  @PatchMapping("/admin/users/{id}/toggle") Map<String,Object> toggle(@PathVariable String id,HttpServletRequest r){role(user(r),"admin");if(id.equals(String.valueOf(user(r).get("_id"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot deactivate yourself");Map<String,Object>u=db.byId(id,"users");if(u==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");u.put("isActive",!Boolean.FALSE.equals(u.get("isActive")));db.save(u,"users");return out("user",safe(u));}
  @GetMapping("/admin/stats") Map<String,Object> stats(HttpServletRequest r){role(user(r),"admin");List<Map<String,Object>> incidents=db.all("incidents");Map<String,Long> cats=new LinkedHashMap<>(),statuses=new LinkedHashMap<>(),locs=new LinkedHashMap<>();for(Map<String,Object> i:incidents){cats.merge(String.valueOf(i.get("category")),1L,Long::sum);statuses.merge(String.valueOf(i.get("status")),1L,Long::sum);locs.merge(String.valueOf(i.get("locationName")),1L,Long::sum);}long active=incidents.stream().filter(i->!"Resolved".equals(i.get("status"))).count();return out("counts",out("total",incidents.size(),"active",active,"resolved",incidents.size()-active,"users",db.count("users")),"byCategory",cats.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"byStatus",statuses.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"byLocation",locs.entrySet().stream().map(e->out("_id",e.getKey(),"count",e.getValue())).toList(),"overTime",List.of(),"avgResponseMinutes",null);}
}


