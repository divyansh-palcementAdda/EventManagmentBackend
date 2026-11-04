package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.AttendeeRegistrationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendeeRegistrationAnswerRepository extends JpaRepository<AttendeeRegistrationAnswer, UUID> {

    List<AttendeeRegistrationAnswer> findByPassId(UUID passId);

    List<AttendeeRegistrationAnswer> findByAttendeeId(UUID attendeeId);

    void deleteByPassId(UUID passId);
}
