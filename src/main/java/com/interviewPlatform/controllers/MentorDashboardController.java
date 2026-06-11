package com.interviewPlatform.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.interviewPlatform.dtos.response.MentorProfileResponseDTO;
import com.interviewPlatform.dtos.response.StudentProfileResponseDTO;
import com.interviewPlatform.repositories.InterviewRequestRepository;
import com.interviewPlatform.repositories.MentorRepository;
import com.interviewPlatform.repositories.StudentApplicationRepository;
import com.interviewPlatform.repositories.StudentRepository;
import com.interviewPlatform.repositories.InterviewEvaluationRepository;
import com.interviewPlatform.entities.Mentor;
import com.interviewPlatform.entities.Student;
import com.interviewPlatform.enums.Status;
import com.interviewPlatform.services.MentorService;
import com.interviewPlatform.services.StudentFeedbackService;
import com.interviewPlatform.dtos.response.StudentFeedbackReportDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mentor")
@RequiredArgsConstructor
public class MentorDashboardController {

    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final InterviewRequestRepository interviewRequestRepository;
    private final StudentApplicationRepository applicationRepository;
    private final StudentFeedbackService feedbackService;
    private final MentorService mentorService;
    private final InterviewEvaluationRepository interviewEvaluationRepository;

    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/me")
    public ResponseEntity<MentorProfileResponseDTO> getMyProfile(Authentication auth) {

        Mentor mentor = mentorRepository.findByUserEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("Mentor not found"));

        MentorProfileResponseDTO dto = new MentorProfileResponseDTO(
            mentor.getId(),
            mentor.getFirstName(),
            mentor.getLastName(),
            mentor.getUser().getEmail(),
            mentor.getPhone(),
            mentor.getDesignation(),
            mentor.getDepartment() != null ? mentor.getDepartment().getId() : null,
            mentor.getDepartment() != null ? mentor.getDepartment().getName() : null,
            mentor.getInstitute() != null ? mentor.getInstitute().getId() : null,
            mentor.getInstitute() != null ? mentor.getInstitute().getInstituteName() : null,
            mentor.getProfilePhotoUrl()
        );

        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/students")
    public ResponseEntity<List<StudentProfileResponseDTO>> getMyStudents(Authentication auth) {

        Mentor mentor = mentorRepository.findByUserEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("Mentor not found"));

        List<Student> students = studentRepository
            .findByDepartmentId(mentor.getDepartment().getId());

        List<StudentProfileResponseDTO> dtos = students.stream()
            .map(s -> {
                String resumeFileName = s.getResumeUrl();
                String resumeUrl = (resumeFileName != null && !resumeFileName.isBlank())
                    ? "/uploads/" + resumeFileName
                    : null;
                
                List<Double> scores = applicationRepository.findByStudentId(s.getId()).stream()
                    .map(app -> interviewEvaluationRepository.findByApplicationId(app.getId()).orElse(null))
                    .filter(e -> e != null && e.getOverallScore() != null)
                    .map(com.interviewPlatform.entities.InterviewEvaluation::getOverallScore)
                    .toList();
                Double averageScore = scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                return new StudentProfileResponseDTO(
                    s.getId(),
                    s.getFirstName(),
                    s.getLastName(),
                    s.getUser().getEmail(),
                    s.getPhone(),
                    s.getStudentClass(),
                    s.getCgpa(),
                    s.getSkills(),
                    s.getInstitute() != null ? s.getInstitute().getId() : null,
                    s.getInstitute() != null ? s.getInstitute().getInstituteName() : null,
                    s.getDepartment() != null ? s.getDepartment().getId() : null,
                    s.getDepartment() != null ? s.getDepartment().getName() : null,
                    resumeFileName,
                    resumeUrl,
                    s.getProjects(),
                    applicationRepository.findByStudentId(s.getId()).stream()
                        .filter(a -> (a.getStatus() == Status.APPROVED || a.getStatus() == Status.COMPLETED) && 
                                     a.getInterviewRequest() != null && 
                                     a.getInterviewRequest().getStatus() == Status.COMPLETED)
                        .count(),
                    s.getProfilePhotoUrl(),
                    averageScore
                );
            })
            .toList();

        return ResponseEntity.ok(dtos);
    }

    // Mentor sees students who have applied for upcoming interviews in their institute
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/upcoming-students")
    public ResponseEntity<?> getUpcomingStudents(Authentication auth) {
        Mentor mentor = mentorRepository.findByUserEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("Mentor not found"));

        Long instituteId = mentor.getInstitute().getId();

        // Get all confirmed/rescheduled interview requests for this institute
        List<com.interviewPlatform.entities.InterviewRequest> upcomingRequests =
            interviewRequestRepository.findByInstituteIdAndStatusIn(
                instituteId,
                List.of(Status.CONFIRMED, Status.RESCHEDULED, Status.PENDING)
            );

        // For each request, get the applicants and build a flat row per student
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.interviewPlatform.entities.InterviewRequest req : upcomingRequests) {
            List<com.interviewPlatform.entities.StudentApplication> applications =
                applicationRepository.findByInterviewRequestId(req.getId());

            for (com.interviewPlatform.entities.StudentApplication app : applications) {
                com.interviewPlatform.entities.Student s = app.getStudent();
                Map<String, Object> row = new HashMap<>();
                // Student fields
                row.put("studentId", s.getId());
                row.put("firstName", s.getFirstName());
                row.put("lastName", s.getLastName());
                row.put("email", s.getUser().getEmail());
                row.put("phone", s.getPhone());
                row.put("studentClass", s.getStudentClass());
                row.put("cgpa", s.getCgpa());
                row.put("skills", s.getSkills());
                String resumeFileName = s.getResumeUrl();
                row.put("resumeFileName", resumeFileName);
                row.put("resumeUrl", (resumeFileName != null && !resumeFileName.isBlank())
                    ? "/uploads/" + resumeFileName
                    : null);
                // Application fields
                row.put("applicationId", app.getId());
                row.put("applicationStatus", app.getStatus() != null ? app.getStatus().name() : "PENDING");
                row.put("appliedAt", app.getAppliedAt());
                // Interview request fields
                row.put("interviewId", req.getId());
                row.put("departmentName", req.getDepartmentName());
                row.put("scheduledDate", req.getScheduledDate());
                row.put("scheduledVenue", req.getScheduledVenue());
                row.put("expertise", req.getExpertise());
                row.put("interviewStatus", req.getStatus().name());
                result.add(row);
            }
        }

        return ResponseEntity.ok(result);
    }

    // Mentor sees confirmed/rescheduled interviews for their institute + applicant counts
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/interviews")
    public ResponseEntity<?> getScheduledInterviews(Authentication auth) {
    Mentor mentor = mentorRepository.findByUserEmail(auth.getName())
        .orElseThrow(() -> new RuntimeException("Mentor not found"));

    List<Map<String, Object>> result =
        interviewRequestRepository.findByInstituteId(mentor.getInstitute().getId())
        .stream()
        .filter(r -> r.getStatus() == Status.CONFIRMED || r.getStatus() == Status.RESCHEDULED)
        .map(r -> {
            long count = applicationRepository.countByInterviewRequestId(r.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("departmentName", r.getDepartmentName());
            m.put("scheduledDate", r.getScheduledDate());
            m.put("scheduledVenue", r.getScheduledVenue());
            m.put("meetingLink", r.getMeetingLink());
            m.put("status", r.getStatus().name());
            m.put("applicantCount", count);
            return m;
        }).toList();

    return ResponseEntity.ok(result);
}

    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/reports-data")
    public ResponseEntity<Map<String, Object>> getReportsData(Authentication auth) {
        Mentor mentor = mentorRepository.findByUserEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("Mentor not found"));

        List<Student> students = studentRepository.findByDepartmentId(mentor.getDepartment().getId());
        List<Long> studentIds = students.stream().map(Student::getId).toList();

        List<com.interviewPlatform.entities.InterviewEvaluation> evals = interviewEvaluationRepository.findAll().stream()
            .filter(e -> e.getApplication().getStudent() != null && studentIds.contains(e.getApplication().getStudent().getId()))
            .toList();

        long outstanding = evals.stream().filter(e -> e.getOverallScore() >= 9.0).count();
        long good = evals.stream().filter(e -> e.getOverallScore() >= 6.0 && e.getOverallScore() < 9.0).count();
        long below = evals.stream().filter(e -> e.getOverallScore() < 6.0).count();
        long notEval = students.size() - evals.stream().map(e -> e.getApplication().getStudent().getId()).distinct().count();

        List<String> labels = new java.util.ArrayList<>();
        List<Long> completed = new java.util.ArrayList<>();
        List<Long> scheduled = new java.util.ArrayList<>();
        List<Double> avgScores = new java.util.ArrayList<>();

        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        for (int i = 5; i >= 0; i--) {
            int m = now.getMonthValue() - i;
            int y = now.getYear();
            if (m <= 0) { m += 12; y -= 1; }
            
            java.time.LocalDateTime start = java.time.LocalDateTime.of(y, m, 1, 0, 0);
            java.time.LocalDateTime end = start.plusMonths(1);

            long comp = applicationRepository.findAll().stream()
                .filter(a -> a.getStudent() != null && studentIds.contains(a.getStudent().getId())
                    && a.getStatus() == Status.COMPLETED
                    && a.getAppliedAt() != null && a.getAppliedAt().isAfter(start) && a.getAppliedAt().isBefore(end))
                .count();
                
            long sched = applicationRepository.findAll().stream()
                .filter(a -> a.getStudent() != null && studentIds.contains(a.getStudent().getId())
                    && a.getStatus() == Status.APPROVED
                    && a.getInterviewRequest() != null 
                    && a.getInterviewRequest().getScheduledDate() != null
                    && a.getInterviewRequest().getScheduledDate().isAfter(start)
                    && a.getInterviewRequest().getScheduledDate().isBefore(end))
                .count();

            double avg = evals.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(start) && e.getCreatedAt().isBefore(end))
                .mapToDouble(com.interviewPlatform.entities.InterviewEvaluation::getOverallScore).average().orElse(0.0);

            labels.add(months[m - 1]);
            completed.add(comp);
            scheduled.add(sched);
            avgScores.add(Math.round(avg * 10.0) / 10.0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scoreDistribution", List.of(outstanding, good, below, notEval));
        
        Map<String, Object> monthly = new HashMap<>();
        monthly.put("labels", labels);
        monthly.put("completed", completed);
        monthly.put("scheduled", scheduled);
        monthly.put("avgScores", avgScores);
        result.put("monthlyStats", monthly);

        return ResponseEntity.ok(result);
    }

    // Mentor sees student feedback reports
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/students/{studentId}/feedback-reports")
    public ResponseEntity<List<StudentFeedbackReportDTO>> getStudentFeedbackReports(@PathVariable Long studentId) {
        return ResponseEntity.ok(feedbackService.getFeedbackReportsByStudentId(studentId));
    }

    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(value = "/me/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPhoto(Authentication authentication, @RequestPart("photo") org.springframework.web.multipart.MultipartFile photoFile) {
        return ResponseEntity.ok(mentorService.uploadMyProfilePhoto(authentication.getName(), photoFile));
    }
}