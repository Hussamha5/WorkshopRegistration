package patmal.workshops.student.impl.logic;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import patmal.workshops.api.logic.WorkshopSystem;
import patmal.workshops.api.model.Instructor;
import patmal.workshops.api.model.Student;
import patmal.workshops.api.model.Team;
import patmal.workshops.api.model.Workshop;
import patmal.workshops.student.impl.model.InstructorImpl;
import patmal.workshops.student.impl.model.StudentImpl;
import patmal.workshops.student.impl.model.TeamImpl;
import patmal.workshops.student.impl.model.WorkshopImpl;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of WorkshopSystem.
 *
 * Unknown keys policy (section 3.3): a lookup with an id that does not exist
 * returns null (getX(id)) or an empty list (getStudentsOfX(id)). No exception is thrown.
 */
public class WorkshopSystemImpl implements WorkshopSystem {
    private static final Logger log = LogManager.getLogger(WorkshopSystemImpl.class);

    // LinkedHashMap keeps creation order
    private final Map<String, InstructorImpl> instructors = new LinkedHashMap<>();
    private final Map<String, StudentImpl> students = new LinkedHashMap<>();
    private final Map<Integer, WorkshopImpl> workshops = new LinkedHashMap<>();
    private final Map<Integer, TeamImpl> teams = new LinkedHashMap<>();

    private int nextWorkshopId = 1;
    private int nextTeamId = 1;

    private final JsonExporter jsonExporter = new JsonExporter();

    public WorkshopSystemImpl() {
    }

    // ---------------- creating ----------------

    @Override
    public Instructor createInstructor(String id, String name, String email) {
        if (id == null) {
            throw rejected("Instructor id must not be null");
        }
        if (instructors.containsKey(id)) {
            throw rejected("Instructor with id " + id + " already exists");
        }
        validateEmail(email, "instructor " + id);
        InstructorImpl instructor = new InstructorImpl(id, name, email);
        instructors.put(id, instructor);
        log.info("Created instructor {} ({}, {})", id, name, email);
        return instructor;
    }

    @Override
    public Student createStudent(String id, String name, String email) {
        if (id == null) {
            throw rejected("Student id must not be null");
        }
        if (students.containsKey(id)) {
            throw rejected("Student with id " + id + " already exists");
        }
        validateEmail(email, "student " + id);
        StudentImpl student = new StudentImpl(id, name, email);
        students.put(id, student);
        log.info("Created student {} ({}, {})", id, name, email);
        return student;
    }

    @Override
    public Workshop createWorkshop(String name, String description) {
        WorkshopImpl workshop = new WorkshopImpl(nextWorkshopId, name, description);
        workshops.put(workshop.getId(), workshop);
        nextWorkshopId++;
        log.info("Created workshop {} ({})", workshop.getId(), name);
        return workshop;
    }

    @Override
    public void associateWorkshopWithInstructor(Instructor instructor, Workshop workshop) {
        if (instructor == null || workshop == null) {
            throw rejected("Instructor and workshop must not be null");
        }
        InstructorImpl myInstructor = instructors.get(instructor.getId());
        WorkshopImpl myWorkshop = workshops.get(workshop.getId());
        if (myInstructor == null) {
            throw rejected("Unknown instructor " + instructor.getId());
        }
        if (myWorkshop == null) {
            throw rejected("Unknown workshop " + workshop.getId());
        }

        // if the workshop had a different instructor before - remove it from him/her
        Instructor old = myWorkshop.getInstructor();
        if (old != null && old != myInstructor) {
            instructors.get(old.getId()).removeWorkshop(myWorkshop);
        }

        // update both sides of the relation
        myWorkshop.setInstructor(myInstructor);
        myInstructor.addWorkshop(myWorkshop);
        log.info("Workshop {} is now given by instructor {}", myWorkshop.getId(), myInstructor.getId());
    }

    @Override
    public Team createTeam(String name, Workshop workshop, List<String> members) {
        // 1. validate EVERYTHING before changing anything
        if (workshop == null) {
            throw rejected("Workshop must not be null");
        }
        WorkshopImpl myWorkshop = workshops.get(workshop.getId());
        if (myWorkshop == null) {
            throw rejected("Unknown workshop " + workshop.getId());
        }
        if (members == null || members.isEmpty()) {
            throw rejected("A team must have at least one student");
        }

        List<StudentImpl> toAdd = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String studentId : members) {
            if (!seen.add(studentId)) {
                throw rejected("Student " + studentId + " is listed twice");
            }
            StudentImpl student = students.get(studentId);
            if (student == null) {
                throw rejected("Unknown student " + studentId);
            }
            if (student.getTeam() != null) {
                throw rejected("Student " + studentId + " is already in a team");
            }
            toAdd.add(student);
        }

        // 2. everything is valid - now apply the changes
        TeamImpl team = new TeamImpl(nextTeamId, name, myWorkshop);
        nextTeamId++;
        for (StudentImpl student : toAdd) {
            team.addStudent(student);
            student.setTeam(team);
        }
        teams.put(team.getId(), team);
        log.info("Created team {} ({}) in workshop {} with students {}", team.getId(), name, myWorkshop.getId(), members);
        return team;
    }

    // ---------------- querying ----------------

    @Override
    public List<Instructor> getInstructors() {
        return new ArrayList<>(instructors.values());
    }

    @Override
    public Instructor getInstructor(String id) {
        return instructors.get(id); // null if unknown
    }

    @Override
    public List<Student> getStudents() {
        return new ArrayList<>(students.values());
    }

    @Override
    public Student getStudent(String id) {
        return students.get(id); // null if unknown
    }

    @Override
    public List<Workshop> getWorkshops() {
        return new ArrayList<>(workshops.values());
    }

    @Override
    public Workshop getWorkshop(Integer id) {
        return workshops.get(id); // null if unknown
    }

    @Override
    public List<Team> getTeams() {
        return new ArrayList<>(teams.values());
    }

    @Override
    public Team getTeam(Integer id) {
        return teams.get(id); // null if unknown
    }

    @Override
    public List<Student> getFreeStudents() {
        List<Student> result = new ArrayList<>();
        for (StudentImpl student : students.values()) {
            if (student.getTeam() == null) {
                result.add(student);
            }
        }
        return result;
    }

    @Override
    public List<Student> getStudentsOfInstructor(String instructorId) {
        List<Student> result = new ArrayList<>();
        InstructorImpl instructor = instructors.get(instructorId);
        if (instructor == null) {
            return result; // unknown instructor -> empty list
        }
        for (Workshop workshop : instructor.getWorkshops()) {
            for (Student student : getStudentsOfWorkshop(workshop.getId())) {
                if (!result.contains(student)) {
                    result.add(student);
                }
            }
        }
        return result;
    }

    @Override
    public List<Student> getStudentsOfWorkshop(Integer workshopId) {
        List<Student> result = new ArrayList<>();
        for (TeamImpl team : teams.values()) {
            if (team.getWorkshop().getId().equals(workshopId)) {
                for (Student student : team.getStudents()) {
                    if (!result.contains(student)) {
                        result.add(student);
                    }
                }
            }
        }
        return result; // unknown workshop -> empty list
    }

    // ---------------- exporting (new in API 2.0) ----------------

    @Override
    public void exportInstructorsDataToJson(File targetFileName) {
        jsonExporter.write(targetFileName, jsonExporter.buildInstructors(getInstructors(), getTeams()));
        log.info("Exported {} instructors to {}", instructors.size(), targetFileName);
    }

    @Override
    public void exportStudentsDataToJson(File targetFileName) {
        jsonExporter.write(targetFileName, jsonExporter.buildStudents(getStudents(), getTeams()));
        log.info("Exported {} students to {}", students.size(), targetFileName);
    }

    @Override
    public void exportTeamsDataToJson(File targetFileName) {
        jsonExporter.write(targetFileName, jsonExporter.buildTeams(getTeams()));
        log.info("Exported {} teams to {}", teams.size(), targetFileName);
    }

    // ---------------- importing (bonus) ----------------

    /**
     * Registers students from a UTF-8 CSV file with the header line "id,name,email".
     * Every line is registered exactly as if createStudent had been called with its values.
     * The import stops at the first rejected line (IllegalArgumentException); the students
     * of the lines before it stay registered.
     */
    @Override
    public List<Student> importStudentDataFromFile(File source) {
        if (source == null) {
            throw rejected("Source file must not be null");
        }
        List<Student> imported = new ArrayList<>();
        // CSVFormat.DEFAULT: comma separated, values may be quoted, empty lines are ignored
        try (Reader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {

            Iterator<CSVRecord> lines = parser.iterator();
            if (!lines.hasNext()) {
                throw rejected("The file " + source + " is empty (no header line)");
            }
            checkHeader(lines.next(), source);

            while (lines.hasNext()) {
                CSVRecord line = lines.next();
                if (line.size() != 3) {
                    throw rejected("Line " + line.getRecordNumber() + " of " + source
                            + " must have exactly 3 values (id, name, email) but has " + line.size());
                }
                // the same rules as createStudent: a rejected line throws and stops the import
                imported.add(createStudent(line.get(0), line.get(1), line.get(2)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the file " + source, e);
        }
        log.info("Imported {} students from {}", imported.size(), source);
        return imported;
    }

    private void checkHeader(CSVRecord header, File source) {
        String first = header.size() > 0 ? header.get(0).replace("\uFEFF", "") : ""; // ignore a UTF-8 BOM
        boolean ok = header.size() == 3
                && first.equals("id")
                && header.get(1).equals("name")
                && header.get(2).equals("email");
        if (!ok) {
            throw rejected("Wrong header line in " + source + ": expected id,name,email");
        }
    }

    // ---------------- helpers ----------------

    /** E-mail validation with commons-validator. null, empty and blank are invalid as well. */
    private void validateEmail(String email, String owner) {
        if (!EmailValidator.getInstance().isValid(email)) {
            throw rejected("Invalid e-mail address for " + owner + ": " + email);
        }
    }

    /** Logs a rejected operation at warn level and returns the exception to throw. */
    private IllegalArgumentException rejected(String message) {
        log.warn("Rejected: {}", message);
        return new IllegalArgumentException(message);
    }
}
