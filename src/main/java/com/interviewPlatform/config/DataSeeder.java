package com.interviewPlatform.config;

import com.interviewPlatform.entities.*;
import com.interviewPlatform.enums.Role;
import com.interviewPlatform.enums.Status;
import com.interviewPlatform.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final InstituteRepository instituteRepository;
    private final DepartmentRepository departmentRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final InterviewerRepository interviewerRepository;
    private final InterviewRequestRepository interviewRequestRepository;
    private final StudentApplicationRepository studentApplicationRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.findByEmail("institute1@platform.com").isPresent()) {
            log.info("Dummy data already exists. Skipping Data Seeder.");
            return;
        }

        log.info("Starting Data Seeder: Generating dummy data...");

        // Ensure directories and dummy files exist
        setupDummyFiles();

        // 1. Create Admin (only if not present)
        if (userRepository.findByEmail("admin@platform.com").isEmpty()) {
            createUser("admin@platform.com", "admin123", Role.ADMIN);
        }

        // 2. Create Institute
        User instUser = createUser("institute1@platform.com", "inst123", Role.INSTITUTE);
        Institute institute = new Institute();
        institute.setUser(instUser);
        institute.setInstituteName("Pune Institute of Technology");
        institute.setUniversity("Pune University");
        institute.setInstituteCode("PIT" + new Random().nextInt(1000));
        institute.setAddress("Shivaji Nagar");
        institute.setCity("Pune");
        institute.setState("Maharashtra");
        institute.setAdminName("Dr. Vikram Desai");
        institute.setDesignation("Director");
        institute.setContactNumber("9876543210");
        institute.setStudentStrength(500);
        instituteRepository.save(institute);

        // 3. Create Department
        Department csDept = new Department();
        csDept.setName("Computer Science");
        csDept.setInstitute(institute);
        departmentRepository.save(csDept);

        // 4. Create Mentor
        User mentorUser = createUser("mentor1@platform.com", "mentor123", Role.MENTOR);
        Mentor mentor = new Mentor();
        mentor.setUser(mentorUser);
        mentor.setFirstName("Rahul");
        mentor.setLastName("Sharma");
        mentor.setPhone("9876543211");
        mentor.setDesignation("Senior Professor");
        mentor.setDepartment(csDept);
        mentor.setInstitute(institute);
        mentor.setSkills(Arrays.asList("Java", "Spring Boot"));
        mentor.setProfilePhotoUrl("https://ui-avatars.com/api/?name=Rahul+Sharma&background=random");
        mentorRepository.save(mentor);

        // 5. Create Students
        Student s1 = createStudent("amit.singh@platform.com", "Amit", "Singh", institute, csDept);
        Student s2 = createStudent("neha.gupta@platform.com", "Neha", "Gupta", institute, csDept);
        Student s3 = createStudent("vikram.joshi@platform.com", "Vikram", "Joshi", institute, csDept);

        // 6. Create Interviewers
        Interviewer int1 = createInterviewer("sanjay.kumar@platform.com", "Sanjay Kumar", "TCS");
        Interviewer int2 = createInterviewer("anjali.desai@platform.com", "Anjali Desai", "Infosys");

        // 7. Create Dummy Data for last 3 months
        Random random = new Random();
        for (int i = 0; i < 15; i++) {
            boolean isCompleted = i < 12;
            LocalDateTime randomDate;
            if (isCompleted) {
                // Past dates: between 3 and 90 days ago
                randomDate = LocalDateTime.now().minusDays(random.nextInt(88) + 3);
            } else {
                // Future dates: between 1 and 14 days in the future
                randomDate = LocalDateTime.now().plusDays(random.nextInt(14) + 1);
            }

            // Create Request
            InterviewRequest req = new InterviewRequest();
            req.setDepartmentName("Computer Science");
            req.setExpertise(Arrays.asList("Java", "React"));
            req.setStartDate(randomDate.plusDays(1));
            req.setEndDate(randomDate.plusDays(2));
            req.setContactPerson("Rahul Sharma");
            req.setContactEmail("mentor1@platform.com");
            req.setStatus(isCompleted ? Status.COMPLETED : Status.CONFIRMED);
            req.setInstitute(institute);
            req.setNumberOfInterviewers(1);
            req.setAssignedInterviewer(i % 2 == 0 ? int1 : int2);
            req.setScheduledDate(randomDate.plusDays(2));
            req.setMeetingLink("https://meet.google.com/abc-xyz-def");
            req.setInstituteConfirmed(true);
            req.setCreatedAt(randomDate);
            interviewRequestRepository.save(req);

            if (isCompleted) {
                // Create Application
                StudentApplication app1 = new StudentApplication();
                app1.setStudent(i % 3 == 0 ? s1 : (i % 3 == 1 ? s2 : s3));
                app1.setInterviewRequest(req);
                app1.setAssignedInterviewer(req.getAssignedInterviewer());
                app1.setStatus(Status.COMPLETED);
                app1.setAppliedAt(randomDate.plusDays(1));
                app1.setVideoUrl("dummy_video.mp4");
                studentApplicationRepository.save(app1);

                // Create Evaluation
                InterviewEvaluation eval = new InterviewEvaluation();
                eval.setApplication(app1);
                eval.setInterviewer(req.getAssignedInterviewer());
                eval.setTechnicalScore(7 + random.nextInt(4)); // 7-10
                eval.setCommunicationScore(6 + random.nextInt(5)); // 6-10
                eval.setDomainScore(7 + random.nextInt(4));
                eval.setApproachScore(8 + random.nextInt(3));
                eval.setConfidenceScore(7 + random.nextInt(4));
                eval.setOverallPerformance(eval.getTechnicalScore() > 8 ? "Excellent" : "Good");
                eval.setStrengths("Strong technical skills, good attitude.");
                eval.setImprovements("Needs to work on system design concepts.");
                eval.setOverallScore((eval.getTechnicalScore() + eval.getCommunicationScore() + eval.getDomainScore() + eval.getApproachScore() + eval.getConfidenceScore()) / 5.0);
                eval.setCreatedAt(randomDate.plusDays(2));
                interviewEvaluationRepository.save(eval);
            }
        }

        log.info("Data Seeder completed successfully!");
    }

    private User createUser(String email, String pwd, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(pwd));
        user.setRole(role);
        user.setStatus(Status.ACTIVE);
        return userRepository.save(user);
    }

    private Student createStudent(String email, String first, String last, Institute inst, Department dept) {
        User u = createUser(email, "student123", Role.STUDENT);
        Student s = new Student();
        s.setUser(u);
        s.setFirstName(first);
        s.setLastName(last);
        s.setPhone("9000000000");
        s.setStudentClass("BE");
        s.setCgpa(8.5);
        s.setInstitute(inst);
        s.setDepartment(dept);
        s.setSkills(Arrays.asList("Java", "HTML", "CSS"));
        s.setProfilePhotoUrl("https://ui-avatars.com/api/?name=" + first + "+" + last + "&background=random");
        s.setResumeUrl("dummy_resume.pdf");
        return studentRepository.save(s);
    }

    private Interviewer createInterviewer(String email, String name, String company) {
        User u = createUser(email, "interviewer123", Role.INTERVIEWER);
        Interviewer i = new Interviewer();
        i.setUser(u);
        i.setFullName(name);
        i.setPhone("8000000000");
        i.setJobTitle("Software Engineer");
        i.setCompany(company);
        i.setExperience("5 years");
        i.setSkills(Arrays.asList("Java", "Spring", "React"));
        i.setProfilePhotoUrl("https://ui-avatars.com/api/?name=" + name.replace(" ", "+") + "&background=random");
        i.setResumeUrl("dummy_resume.pdf");
        return interviewerRepository.save(i);
    }

    private void setupDummyFiles() {
        try {
            Path uploadsDir = Paths.get("uploads");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }
            Path resumesDir = uploadsDir.resolve("resumes");
            if (!Files.exists(resumesDir)) {
                Files.createDirectories(resumesDir);
            }
            Path dummyResume = resumesDir.resolve("dummy_resume.pdf");
            if (!Files.exists(dummyResume)) {
                Files.write(dummyResume, "%PDF-1.4\n%Dummy PDF\n".getBytes());
            }
            
            // Because the code might refer to uploads/dummy_resume.pdf or uploads/resumes/dummy_resume.pdf, 
            // let's place one in the root of uploads too.
            Path dummyResumeRoot = uploadsDir.resolve("dummy_resume.pdf");
            if (!Files.exists(dummyResumeRoot)) {
                Files.write(dummyResumeRoot, "%PDF-1.4\n%Dummy PDF\n".getBytes());
            }

            Path dummyVideo = uploadsDir.resolve("dummy_video.mp4");
            if (!Files.exists(dummyVideo)) {
                Files.write(dummyVideo, "dummy video content".getBytes());
            }
        } catch (Exception e) {
            log.error("Failed to setup dummy files", e);
        }
    }
}
