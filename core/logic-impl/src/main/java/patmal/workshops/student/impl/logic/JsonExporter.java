package patmal.workshops.student.impl.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import patmal.workshops.api.model.Instructor;
import patmal.workshops.api.model.Person;
import patmal.workshops.api.model.Student;
import patmal.workshops.api.model.Team;
import patmal.workshops.api.model.Workshop;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds the three JSON exports with Jackson.
 *
 * Every export is built as a tree of JSON nodes (ObjectNode / ArrayNode) and only then
 * written to the file. Because we decide by hand which fields go into every node, there
 * are no cycles: inside a team the students are written with their own details only,
 * never with their team again.
 */
class JsonExporter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // ---------------- the three exports ----------------

    /** instructor -> workshops -> teams -> students (details only) */
    ArrayNode buildInstructors(List<Instructor> instructors, List<Team> teams) {
        ArrayNode result = mapper.createArrayNode();
        for (Instructor instructor : instructors) {
            ObjectNode instructorNode = personDetails(instructor);
            ArrayNode workshopsNode = instructorNode.putArray("workshops");
            for (Workshop workshop : instructor.getWorkshops()) {
                ObjectNode workshopNode = workshopDetails(workshop);
                ArrayNode teamsNode = workshopNode.putArray("teams");
                for (Team team : teams) {
                    if (sameWorkshop(team.getWorkshop(), workshop)) {
                        ObjectNode teamNode = mapper.createObjectNode();
                        teamNode.put("id", team.getId());
                        teamNode.put("name", team.getName());
                        teamNode.set("students", studentsDetails(team.getStudents()));
                        teamsNode.add(teamNode);
                    }
                }
                workshopsNode.add(workshopNode);
            }
            result.add(instructorNode);
        }
        return result;
    }

    /** student -> team (with the ids of its students) -> workshop -> instructor */
    ArrayNode buildStudents(List<Student> students, List<Team> teams) {
        ArrayNode result = mapper.createArrayNode();
        for (Student student : students) {
            ObjectNode studentNode = personDetails(student);
            Team team = findTeamOf(student, teams);
            if (team == null) {
                studentNode.putNull("team"); // a free student
            } else {
                ObjectNode teamNode = studentNode.putObject("team");
                teamNode.put("id", team.getId());
                teamNode.put("name", team.getName());
                ArrayNode idsNode = teamNode.putArray("studentIds");
                for (Student member : team.getStudents()) {
                    idsNode.add(member.getId());
                }
                teamNode.set("workshop", workshopWithInstructor(team.getWorkshop()));
            }
            result.add(studentNode);
        }
        return result;
    }

    /** team -> students (details only) + workshop -> instructor */
    ArrayNode buildTeams(List<Team> teams) {
        ArrayNode result = mapper.createArrayNode();
        for (Team team : teams) {
            ObjectNode teamNode = mapper.createObjectNode();
            teamNode.put("id", team.getId());
            teamNode.put("name", team.getName());
            teamNode.set("students", studentsDetails(team.getStudents()));
            teamNode.set("workshop", workshopWithInstructor(team.getWorkshop()));
            result.add(teamNode);
        }
        return result;
    }

    /** Writes the tree to the file in UTF-8 (Jackson's default). An existing file is overwritten. */
    void write(File target, ArrayNode tree) {
        if (target == null) {
            throw new IllegalArgumentException("Target file must not be null");
        }
        try {
            mapper.writeValue(target, tree);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the file " + target, e);
        }
    }

    // ---------------- small building blocks ----------------

    /** The details of a person: exactly id, name and email. */
    private ObjectNode personDetails(Person person) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", person.getId());
        node.put("name", person.getName());
        node.put("email", person.getEmail());
        return node;
    }

    /** The details of a workshop: exactly id, name and description. */
    private ObjectNode workshopDetails(Workshop workshop) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", workshop.getId());
        node.put("name", workshop.getName());
        node.put("description", workshop.getDescription());
        return node;
    }

    /** The details of a workshop plus its instructor (null when it has none). */
    private ObjectNode workshopWithInstructor(Workshop workshop) {
        if (workshop == null) {
            return null; // cannot happen: every team has a workshop
        }
        ObjectNode node = workshopDetails(workshop);
        Instructor instructor = workshop.getInstructor();
        if (instructor == null) {
            node.putNull("instructor");
        } else {
            node.set("instructor", personDetails(instructor));
        }
        return node;
    }

    private ArrayNode studentsDetails(List<Student> students) {
        ArrayNode node = mapper.createArrayNode();
        for (Student student : students) {
            node.add(personDetails(student));
        }
        return node;
    }

    private Team findTeamOf(Student student, List<Team> teams) {
        for (Team team : teams) {
            for (Student member : team.getStudents()) {
                if (member.getId().equals(student.getId())) {
                    return team;
                }
            }
        }
        return null; // a free student
    }

    private boolean sameWorkshop(Workshop a, Workshop b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }
}
