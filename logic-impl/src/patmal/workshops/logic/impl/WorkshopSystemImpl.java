package patmal.workshops.logic.impl;

import patmal.workshops.api.logic.WorkshopSystem;
import patmal.workshops.api.model.Instructor;
import patmal.workshops.api.model.Student;
import patmal.workshops.api.model.Team;
import patmal.workshops.api.model.Workshop;
import patmal.workshops.model.impl.InstructorImpl;
import patmal.workshops.model.impl.StudentImpl;
import patmal.workshops.model.impl.TeamImpl;
import patmal.workshops.model.impl.WorkshopImpl;

import java.util.ArrayList;
import java.util.HashSet;
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
    // LinkedHashMap keeps creation order
    private final Map<String, InstructorImpl> instructors = new LinkedHashMap<>();
    private final Map<String, StudentImpl> students = new LinkedHashMap<>();
    private final Map<Integer, WorkshopImpl> workshops = new LinkedHashMap<>();
    private final Map<Integer, TeamImpl> teams = new LinkedHashMap<>();

    private int nextWorkshopId = 1;
    private int nextTeamId = 1;

    public WorkshopSystemImpl() {
    }

    // ---------------- creating ----------------

    @Override
    public Instructor createInstructor(String id, String name, String email) {
        if (id == null) {
            throw new IllegalArgumentException("Instructor id must not be null");
        }
        if (instructors.containsKey(id)) {
            throw new IllegalArgumentException("Instructor with id " + id + " already exists");
        }
        InstructorImpl instructor = new InstructorImpl(id, name, email);
        instructors.put(id, instructor);
        return instructor;
    }

    @Override
    public Student createStudent(String id, String name, String email) {
        if (id == null) {
            throw new IllegalArgumentException("Student id must not be null");
        }
        if (students.containsKey(id)) {
            throw new IllegalArgumentException("Student with id " + id + " already exists");
        }
        StudentImpl student = new StudentImpl(id, name, email);
        students.put(id, student);
        return student;
    }

    @Override
    public Workshop createWorkshop(String name, String description) {
        WorkshopImpl workshop = new WorkshopImpl(nextWorkshopId, name, description);
        workshops.put(workshop.getId(), workshop);
        nextWorkshopId++;
        return workshop;
    }

    @Override
    public void associateWorkshopWithInstructor(Instructor instructor, Workshop workshop) {
        if (instructor == null || workshop == null) {
            throw new IllegalArgumentException("Instructor and workshop must not be null");
        }
        InstructorImpl myInstructor = instructors.get(instructor.getId());
        WorkshopImpl myWorkshop = workshops.get(workshop.getId());
        if (myInstructor == null) {
            throw new IllegalArgumentException("Unknown instructor " + instructor.getId());
        }
        if (myWorkshop == null) {
            throw new IllegalArgumentException("Unknown workshop " + workshop.getId());
        }

        // if the workshop had a different instructor before - remove it from him/her
        Instructor old = myWorkshop.getInstructor();
        if (old != null && old != myInstructor) {
            instructors.get(old.getId()).removeWorkshop(myWorkshop);
        }

        // update both sides of the relation
        myWorkshop.setInstructor(myInstructor);
        myInstructor.addWorkshop(myWorkshop);
    }

    @Override
    public Team createTeam(String name, Workshop workshop, List<String> members) {
        // 1. validate EVERYTHING before changing anything
        if (workshop == null) {
            throw new IllegalArgumentException("Workshop must not be null");
        }
        WorkshopImpl myWorkshop = workshops.get(workshop.getId());
        if (myWorkshop == null) {
            throw new IllegalArgumentException("Unknown workshop " + workshop.getId());
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("A team must have at least one student");
        }

        List<StudentImpl> toAdd = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String studentId : members) {
            if (!seen.add(studentId)) {
                throw new IllegalArgumentException("Student " + studentId + " is listed twice");
            }
            StudentImpl student = students.get(studentId);
            if (student == null) {
                throw new IllegalArgumentException("Unknown student " + studentId);
            }
            if (student.getTeam() != null) {
                throw new IllegalArgumentException("Student " + studentId + " is already in a team");
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
}