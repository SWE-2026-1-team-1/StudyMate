package com.studymate.study.unit;

import com.studymate.study.domain.StudyMember;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyMemberDomainTest {

    // T-MEM-KICK-15
    @Test
    void T_MEM_KICK_15_kick_정상() {
        StudyMember m = StudyMember.createMember(1L, 100L);
        m.kick();
        assertThat(m.isActive()).isFalse();
        assertThat(m.getLeftReason()).isEqualTo("KICKED");
        assertThat(m.getLeftAt()).isNotNull();
        assertThat(m.getUpdatedAt()).isEqualTo(m.getLeftAt());
    }

    // T-MEM-KICK-16
    @Test
    void T_MEM_KICK_16_kick_이미_inactive() {
        StudyMember m = StudyMember.createMember(1L, 100L);
        m.kick();
        assertThatThrownBy(m::kick).isInstanceOf(IllegalStateException.class);
    }

    // T-MEM-LEAVE-10
    @Test
    void T_MEM_LEAVE_10_leave_정상() {
        StudyMember m = StudyMember.createMember(1L, 100L);
        m.leave();
        assertThat(m.isActive()).isFalse();
        assertThat(m.getLeftReason()).isEqualTo("VOLUNTARY");
        assertThat(m.getLeftAt()).isNotNull();
    }

    @Test
    void leave_이미_inactive_예외() {
        StudyMember m = StudyMember.createMember(1L, 100L);
        m.leave();
        assertThatThrownBy(m::leave).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isLeader_분기() {
        StudyMember leader = StudyMember.createLeader(1L, 100L);
        StudyMember member = StudyMember.createMember(1L, 101L);
        assertThat(leader.isLeader()).isTrue();
        assertThat(member.isLeader()).isFalse();
    }
}
