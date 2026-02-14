package com.gengzi.agentteams.service;

import com.gengzi.agentteams.api.CreateTeamRequest;
import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamRegistryServiceTest {

    private final TeamRegistryService service = new TeamRegistryService("claude-opus-4-6-thinking");

    @Test
    void shouldBlockClaimUntilDependenciesCompleted() {
        CreateTeamRequest.TeammateSpec spec = new CreateTeamRequest.TeammateSpec();
        spec.setName("Researcher");
        spec.setRole("Collect facts");
        TeamWorkspace team = service.createTeam("demo", "ship answer", List.of(spec));
        String teammateId = team.getTeammates().values().iterator().next().getId();

        TeamTask t1 = service.createTask(team.getId(), "task1", "base task", List.of(), teammateId);
        TeamTask t2 = service.createTask(team.getId(), "task2", "depends on task1", List.of(t1.getId()), teammateId);

        assertThrows(Exception.class, () -> service.claimTask(team.getId(), t2.getId(), teammateId));

        service.claimTask(team.getId(), t1.getId(), teammateId);
        service.completeTask(team.getId(), t1.getId(), teammateId, "done");
        TeamTask claimed = service.claimTask(team.getId(), t2.getId(), teammateId);
        assertEquals(TaskStatus.IN_PROGRESS, claimed.getStatus());
    }
}
