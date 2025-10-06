
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Proyecto escolar "clinica" - Sistema simple de administración de citas (consola + CSV en ./db)
 * Requisitos:
 *  - Carpeta db/ (archivos CSV) con .gitignore para NO subirlos.
 *  - Si faltan archivos, regenerarlos (ensureDbFiles).
 *  - Funcionalidades: alta doctores, alta pacientes, crear cita, relacionar, control de acceso.
 *  - FAT JAR sencillo (sin dependencias externas).
 *
 * Compilar (carpeta del proyecto):
 *   javac -d out App.java
 * Empaquetar JAR ejecutable:
 *   echo Main-Class: App > MANIFEST.MF
 *   jar cfm clinica.jar MANIFEST.MF -C out .
 * Ejecutar:
 *   java -jar clinica.jar
 */
public class App {

    // ==== Configuración CSV (carpeta db) ====
    static final Path DB_DIR = Paths.get("db");
    static final Path ADMINS_CSV    = DB_DIR.resolve("admins.csv");
    static final Path DOCTORES_CSV  = DB_DIR.resolve("doctores.csv");
    static final Path PACIENTES_CSV = DB_DIR.resolve("pacientes.csv");
    static final Path CITAS_CSV     = DB_DIR.resolve("citas.csv");

    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ==== Modelos simples ====
    static class Admin { String id, nombre, password;
        Admin(String id, String nombre, String password){ this.id=id; this.nombre=nombre; this.password=password; } }
    static class Doctor { String id, nombre, especialidad;
        Doctor(String id, String nombre, String especialidad){ this.id=id; this.nombre=nombre; this.especialidad=especialidad; } }
    static class Paciente { String id, nombre;
        Paciente(String id, String nombre){ this.id=id; this.nombre=nombre; } }
    static class Cita { String id; LocalDateTime fechaHora; String motivo; String doctorId; String pacienteId;
        Cita(String id, LocalDateTime dt, String motivo, String doctorId, String pacienteId){
            this.id=id; this.fechaHora=dt; this.motivo=motivo; this.doctorId=doctorId; this.pacienteId=pacienteId; } }

    // ==== Utilidad CSV simple ====
    static String esc(String s){
        if (s==null) return "";
        boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
        String r = s.replace("\"","\"\"");
        return q ? "\""+r+"\"" : r;
    }
    static List<String> parseCsvLine(String line){
        List<String> out = new ArrayList<>();
        if (line==null) return out;
        StringBuilder sb = new StringBuilder();
        boolean inQ=false;
        for (int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if (c=='"'){
                if (inQ && i+1<line.length() && line.charAt(i+1)=='"'){ sb.append('"'); i++; }
                else inQ=!inQ;
            } else if (c==',' && !inQ){ out.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        out.add(sb.toString());
        return out;
    }

    // ==== Validaciones y util ====
    static boolean idExists(List<Doctor> lst, String id){ return lst.stream().anyMatch(d->d.id.equals(id)); }
    static boolean idExistsP(List<Paciente> lst, String id){ return lst.stream().anyMatch(p->p.id.equals(id)); }
    static boolean idExistsC(List<Cita> lst, String id){ return lst.stream().anyMatch(c->c.id.equals(id)); }
    static boolean doctorExists(List<Doctor> lst, String id){ return id!=null && lst.stream().anyMatch(d->d.id.equals(id)); }
    static boolean pacienteExists(List<Paciente> lst, String id){ return id!=null && lst.stream().anyMatch(p->p.id.equals(id)); }
    static boolean hayConflicto(List<Cita> citas, LocalDateTime dt, String did, String pid, String ignoreId){
        for (Cita c: citas){
            if (ignoreId!=null && c.id.equals(ignoreId)) continue;
            if (c.fechaHora.equals(dt)){
                if (did!=null && did.equals(c.doctorId)) return true;
                if (pid!=null && pid.equals(c.pacienteId)) return true;
            }
        }
        return false;
    }

    // ==== Regeneración de archivos faltantes ====
    static void ensureDbFiles() {
        try {
            if (!Files.exists(DB_DIR)) Files.createDirectories(DB_DIR);
            if (!Files.exists(ADMINS_CSV)) Files.writeString(ADMINS_CSV,
                "admin_id,nombre,password\nadmin,Administrador,admin\n", StandardCharsets.UTF_8);
            if (!Files.exists(DOCTORES_CSV)) Files.writeString(DOCTORES_CSV,
                "doctor_id,nombre,especialidad\n", StandardCharsets.UTF_8);
            if (!Files.exists(PACIENTES_CSV)) Files.writeString(PACIENTES_CSV,
                "paciente_id,nombre\n", StandardCharsets.UTF_8);
            if (!Files.exists(CITAS_CSV)) Files.writeString(CITAS_CSV,
                "cita_id,fecha_hora,motivo,doctor_id,paciente_id\n", StandardCharsets.UTF_8);
        } catch (Exception e){
            System.out.println("[ERROR] Preparando db/: " + e.getMessage());
        }
    }

    // ==== Lectura/Escritura ====
    static List<Admin> loadAdmins(){
        List<Admin> r=new ArrayList<>();
        try(BufferedReader br=Files.newBufferedReader(ADMINS_CSV, StandardCharsets.UTF_8)){
            String line; boolean first=true;
            while((line=br.readLine())!=null){
                if (first){ first=false; continue; }
                if (line.isBlank()) continue;
                var c = parseCsvLine(line);
                if (c.size()<3) continue;
                r.add(new Admin(c.get(0), c.get(1), c.get(2)));
            }
        } catch(Exception e){ System.out.println("[ERROR] Cargando admins: "+e.getMessage()); }
        return r;
    }
    static List<Doctor> loadDoctores(){
        List<Doctor> r=new ArrayList<>();
        try(BufferedReader br=Files.newBufferedReader(DOCTORES_CSV, StandardCharsets.UTF_8)){
            String line; boolean first=true;
            while((line=br.readLine())!=null){
                if (first){ first=false; continue; }
                if (line.isBlank()) continue;
                var c = parseCsvLine(line);
                if (c.size()<3) continue;
                r.add(new Doctor(c.get(0), c.get(1), c.get(2)));
            }
        } catch(Exception e){ System.out.println("[ERROR] Cargando doctores: "+e.getMessage()); }
        return r;
    }
    static List<Paciente> loadPacientes(){
        List<Paciente> r=new ArrayList<>();
        try(BufferedReader br=Files.newBufferedReader(PACIENTES_CSV, StandardCharsets.UTF_8)){
            String line; boolean first=true;
            while((line=br.readLine())!=null){
                if (first){ first=false; continue; }
                if (line.isBlank()) continue;
                var c = parseCsvLine(line);
                if (c.size()<2) continue;
                r.add(new Paciente(c.get(0), c.get(1)));
            }
        } catch(Exception e){ System.out.println("[ERROR] Cargando pacientes: "+e.getMessage()); }
        return r;
    }
    static List<Cita> loadCitas(){
        List<Cita> r=new ArrayList<>();
        try(BufferedReader br=Files.newBufferedReader(CITAS_CSV, StandardCharsets.UTF_8)){
            String line; boolean first=true;
            while((line=br.readLine())!=null){
                if (first){ first=false; continue; }
                if (line.isBlank()) continue;
                var c = parseCsvLine(line);
                if (c.size()<5) continue;
                LocalDateTime dt = LocalDateTime.parse(c.get(1), DT);
                String did = c.get(3).isBlank()? null : c.get(3);
                String pid = c.get(4).isBlank()? null : c.get(4);
                r.add(new Cita(c.get(0), dt, c.get(2), did, pid));
            }
        } catch(Exception e){ System.out.println("[ERROR] Cargando citas: "+e.getMessage()); }
        return r;
    }

    static void saveAdmins(List<Admin> a){
        try(BufferedWriter bw=Files.newBufferedWriter(ADMINS_CSV, StandardCharsets.UTF_8)){
            bw.write("admin_id,nombre,password\n");
            for (Admin x: a) { bw.write(esc(x.id)+","+esc(x.nombre)+","+esc(x.password)+"\n"); }
        } catch(Exception e){ System.out.println("[ERROR] Guardando admins: "+e.getMessage()); }
    }
    static void saveDoctores(List<Doctor> d){
        try(BufferedWriter bw=Files.newBufferedWriter(DOCTORES_CSV, StandardCharsets.UTF_8)){
            bw.write("doctor_id,nombre,especialidad\n");
            for (Doctor x: d) { bw.write(esc(x.id)+","+esc(x.nombre)+","+esc(x.especialidad)+"\n"); }
        } catch(Exception e){ System.out.println("[ERROR] Guardando doctores: "+e.getMessage()); }
    }
    static void savePacientes(List<Paciente> p){
        try(BufferedWriter bw=Files.newBufferedWriter(PACIENTES_CSV, StandardCharsets.UTF_8)){
            bw.write("paciente_id,nombre\n");
            for (Paciente x: p) { bw.write(esc(x.id)+","+esc(x.nombre)+"\n"); }
        } catch(Exception e){ System.out.println("[ERROR] Guardando pacientes: "+e.getMessage()); }
    }
    static void saveCitas(List<Cita> c){
        try(BufferedWriter bw=Files.newBufferedWriter(CITAS_CSV, StandardCharsets.UTF_8)){
            bw.write("cita_id,fecha_hora,motivo,doctor_id,paciente_id\n");
            for (Cita x: c) {
                bw.write(String.join(",",
                    esc(x.id),
                    esc(DT.format(x.fechaHora)),
                    esc(x.motivo),
                    esc(x.doctorId==null? "" : x.doctorId),
                    esc(x.pacienteId==null? "" : x.pacienteId)));
                bw.write("\n");
            }
        } catch(Exception e){ System.out.println("[ERROR] Guardando citas: "+e.getMessage()); }
    }

    // ==== Login simple (acorde a pseudocódigo) ====
    static boolean login(List<Admin> admins, Scanner sc){
        for (int i=0;i<5;i++){
            System.out.print("Usuario: "); String u = sc.nextLine().trim();
            System.out.print("Contraseña: "); String p = sc.nextLine().trim();
            for (Admin a: admins) if (a.id.equals(u) && a.password.equals(p)) return true;
            System.out.println("[ERROR] Credenciales inválidas.\n");
        }
        return false;
    }

    public static void main(String[] args) {
        ensureDbFiles(); // regenerar si faltan
        List<Admin> admins = loadAdmins();
        List<Doctor> doctores = loadDoctores();
        List<Paciente> pacientes = loadPacientes();
        List<Cita> citas = loadCitas();

        Scanner sc = new Scanner(System.in);
        System.out.println("== Sistema de Citas (escolar, CSV en ./db) ==");
        if (!login(admins, sc)) { System.out.println("Demasiados intentos. Saliendo."); return; }
        System.out.println("[OK] Acceso concedido.");

        boolean salir=false;
        while(!salir){
            try {
                System.out.println("\n--- Menú ---");
                System.out.println("1) Alta doctores");
                System.out.println("2) Alta pacientes");
                System.out.println("3) Crear cita");
                System.out.println("4) Relacionar cita (doctor/paciente)");
                System.out.println("5) Listar citas");
                System.out.println("6) Guardar todo");
                System.out.println("7) Salir");
                System.out.print("Elija opción: ");
                String op = sc.nextLine().trim();

                switch(op){
                    case "1" -> {
                        System.out.print("ID doctor: "); String id = sc.nextLine().trim();
                        System.out.print("Nombre: "); String nom = sc.nextLine().trim();
                        System.out.print("Especialidad: "); String esp = sc.nextLine().trim();
                        if (id.isBlank() || nom.isBlank() || esp.isBlank()){ System.out.println("[WARN] Datos faltantes."); break; }
                        if (idExists(doctores, id)){ System.out.println("[WARN] ID duplicado."); break; }
                        doctores.add(new Doctor(id, nom, esp)); saveDoctores(doctores);
                        System.out.println("[OK] Doctor guardado.");
                    }
                    case "2" -> {
                        System.out.print("ID paciente: "); String id = sc.nextLine().trim();
                        System.out.print("Nombre: "); String nom = sc.nextLine().trim();
                        if (id.isBlank() || nom.isBlank()){ System.out.println("[WARN] Datos faltantes."); break; }
                        if (idExistsP(pacientes, id)){ System.out.println("[WARN] ID duplicado."); break; }
                        pacientes.add(new Paciente(id, nom)); savePacientes(pacientes);
                        System.out.println("[OK] Paciente guardado.");
                    }
                    case "3" -> {
                        System.out.print("ID cita: "); String id = sc.nextLine().trim();
                        System.out.print("Fecha (YYYY-MM-DD): "); String f = sc.nextLine().trim();
                        System.out.print("Hora (HH:mm): "); String h = sc.nextLine().trim();
                        System.out.print("Motivo: "); String m = sc.nextLine().trim();
                        if (id.isBlank() || f.isBlank() || h.isBlank() || m.isBlank()){ System.out.println("[WARN] Datos faltantes."); break; }
                        if (idExistsC(citas, id)){ System.out.println("[WARN] ID cita duplicado."); break; }
                        LocalDateTime dt;
                        try { dt = LocalDateTime.parse(f+"T"+h, DT); }
                        catch(Exception e){ System.out.println("[WARN] Fecha/hora inválida."); break; }
                        citas.add(new Cita(id, dt, m, null, null)); saveCitas(citas);
                        System.out.println("[OK] Cita creada (sin asignar).");
                    }
                    case "4" -> {
                        System.out.print("ID cita: "); String cid = sc.nextLine().trim();
                        System.out.print("ID doctor: "); String did = sc.nextLine().trim();
                        System.out.print("ID paciente: "); String pid = sc.nextLine().trim();
                        Cita c = null; for (Cita x: citas) if (x.id.equals(cid)) { c=x; break; }
                        if (c==null){ System.out.println("[WARN] Cita no encontrada."); break; }
                        if (!doctorExists(doctores, did)){ System.out.println("[WARN] Doctor no existe."); break; }
                        if (!pacienteExists(pacientes, pid)){ System.out.println("[WARN] Paciente no existe."); break; }
                        if (hayConflicto(citas, c.fechaHora, did, pid, c.id)){ System.out.println("[WARN] Conflicto de horario."); break; }
                        c.doctorId=did; c.pacienteId=pid; saveCitas(citas);
                        System.out.println("[OK] Relación guardada.");
                    }
                    case "5" -> {
                        System.out.println("Listar por: 1) Todas  2) Fecha  3) Doctor  4) Paciente");
                        String sub = sc.nextLine().trim();
                        List<Cita> r = new ArrayList<>(citas);
                        if ("2".equals(sub)){
                            System.out.print("Fecha (YYYY-MM-DD): "); String d = sc.nextLine().trim();
                            try { LocalDate date = LocalDate.parse(d);
                                  r.removeIf(ci -> !ci.fechaHora.toLocalDate().equals(date));
                            } catch(Exception e){ System.out.println("[WARN] Fecha inválida."); continue; }
                        } else if ("3".equals(sub)){
                            System.out.print("Doctor ID: "); String did = sc.nextLine().trim();
                            r.removeIf(ci -> ci.doctorId==null || !ci.doctorId.equals(did));
                        } else if ("4".equals(sub)){
                            System.out.print("Paciente ID: "); String pid = sc.nextLine().trim();
                            r.removeIf(ci -> ci.pacienteId==null || !ci.pacienteId.equals(pid));
                        }
                        r.sort(Comparator.comparing(ci -> ci.fechaHora));
                        imprimirCitas(r, doctores, pacientes);
                    }
                    case "6" -> {
                        saveAdmins(admins); saveDoctores(doctores); savePacientes(pacientes); saveCitas(citas);
                        System.out.println("[OK] Cambios guardados.");
                    }
                    case "7" -> {
                        saveAdmins(admins); saveDoctores(doctores); savePacientes(pacientes); saveCitas(citas);
                        salir=true;
                    }
                    default -> System.out.println("[WARN] Opción no válida.");
                }
            } catch(Exception e){
                System.out.println("[ERROR] " + e.getMessage()); // continuar
            }
        }
        System.out.println("Hasta luego.");
    }

    static void imprimirCitas(List<Cita> list, List<Doctor> docs, List<Paciente> pacs){
        if (list.isEmpty()){ System.out.println("(sin resultados)"); return; }
        for (Cita c: list){
            String dNom="(sin asignar)"; if (c.doctorId!=null){ for (Doctor d: docs) if (d.id.equals(c.doctorId)) { dNom=d.nombre; break; } }
            String pNom="(sin asignar)"; if (c.pacienteId!=null){ for (Paciente p: pacs) if (p.id.equals(c.pacienteId)) { pNom=p.nombre; break; } }
            System.out.printf("Cita[%s] %s — Dr: %s — Pac: %s — Motivo: %s%n",
                    c.id, DT.format(c.fechaHora), dNom, pNom, c.motivo);
        }
    }
}
