package patmal.workshops.student.impl.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import patmal.workshops.api.logic.WorkshopSystem;
import patmal.workshops.api.model.Instructor;
import patmal.workshops.api.model.Student;
import patmal.workshops.api.model.Workshop;
import patmal.workshops.student.impl.logic.WorkshopSystemImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * workshop-cli: creates a system with demo data and exports it to three JSON files
 * (instructors.json, students.json, teams.json) in the current folder.
 *
 * The demo students are imported from a CSV file:
 *   java -jar workshop-cli.jar my-students.csv   -> the students of the given file
 *   java -jar workshop-cli.jar                   -> students.csv from the resources of the jar
 */
public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("workshop-cli started");
        WorkshopSystem system = new WorkshopSystemImpl();

        // ---- instructors ----
        Instructor dana = system.createInstructor("i1", "Dana Levi", "dana.levi@univ.edu");
        Instructor yossi = system.createInstructor("i2", "Yossi Mizrahi", "yossi.mizrahi@univ.edu");

        // ---- workshops (Dana gives two of them) ----
        Workshop java = system.createWorkshop("Java Workshop", "Object oriented programming in Java");
        Workshop web = system.createWorkshop("Web Development", "HTML, CSS and JavaScript");
        Workshop databases = system.createWorkshop("Databases", "SQL and data modeling");
        system.associateWorkshopWithInstructor(dana, java);
        system.associateWorkshopWithInstructor(dana, web);
        system.associateWorkshopWithInstructor(yossi, databases);

        // ---- students: imported from a CSV file (bonus) ----
        File studentsCsv = args.length > 0 ? new File(args[0]) : copyResourceToTempFile("students.csv");
        log.info("Reading the students from {}", studentsCsv);
        List<Student> imported = system.importStudentDataFromFile(studentsCsv);
        log.info("{} students were imported", imported.size());

        // an invalid e-mail is rejected (and shows up in the log as a warning)
        try {
            system.createStudent("s99", "Bad Mail", "bad-mail.example.com");
        } catch (IllegalArgumentException e) {
            log.info("As expected, a student with an invalid e-mail was not created");
        }

        // ---- teams ----
        system.createTeam("Alpha", java, List.of("s1", "s2"));
        system.createTeam("Beta", web, List.of("s3"));
        system.createTeam("Gamma", databases, List.of("s4", "s5"));

        log.info("Demo data ready: {} instructors, {} workshops, {} students ({} free), {} teams",
                system.getInstructors().size(), system.getWorkshops().size(),
                system.getStudents().size(), system.getFreeStudents().size(), system.getTeams().size());

        // ---- export ----
        File instructorsFile = new File("instructors.json");
        File studentsFile = new File("students.json");
        File teamsFile = new File("teams.json");
        system.exportInstructorsDataToJson(instructorsFile);
        system.exportStudentsDataToJson(studentsFile);
        system.exportTeamsDataToJson(teamsFile);

        log.info("JSON files written to {}", instructorsFile.getAbsoluteFile().getParent());
        log.info("workshop-cli finished");
    }

    /**
     * importStudentDataFromFile needs a File, but inside a jar a resource is not a file.
     * So we copy the resource to a temporary file first.
     */
    private static File copyResourceToTempFile(String resourceName) {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Resource " + resourceName + " was not found");
            }
            Path temp = Files.createTempFile("workshop-cli-", ".csv");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp.toFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the resource " + resourceName, e);
        }
    }
}
