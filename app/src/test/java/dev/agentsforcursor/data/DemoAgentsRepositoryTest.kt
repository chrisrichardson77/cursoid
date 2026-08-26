package dev.agentsforcursor.data

import dev.agentsforcursor.model.AgentMode
import dev.agentsforcursor.model.AgentStatus
import dev.agentsforcursor.model.CreateAgentRequest
import dev.agentsforcursor.model.RunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAgentsRepositoryTest {

    private val repository = DemoAgentsRepository(artifactUri = { "file:///$it" })

    @Test
    fun `hides archived agents unless asked for them`() = runBlocking {
        val visible = repository.agents(includeArchived = false).items
        val all = repository.agents(includeArchived = true).items

        assertTrue(visible.none { it.status == AgentStatus.ARCHIVED })
        assertTrue(all.any { it.status == AgentStatus.ARCHIVED })
        assertEquals(1, visible.count { it.status == AgentStatus.ACTIVE })
    }

    @Test
    fun `seeds each agent with a run that has both sides of the conversation`() = runBlocking {
        for (agent in repository.agents(includeArchived = true).items) {
            val runs = repository.runs(agent.id)
            assertFalse("${agent.name} has no runs", runs.isEmpty())
            assertNotNull("${agent.name} is missing its prompt", runs.first().prompt)
            assertEquals(agent.latestRunId, runs.first().id)
        }
    }

    @Test
    fun `a follow-up puts the agent back to work`() = runBlocking {
        val agent = repository.agents().items.first { it.status == AgentStatus.IDLE }

        val run = repository.followUp(agent.id, "Also add a regression test", AgentMode.AGENT)

        assertEquals(RunStatus.RUNNING, run.status)
        assertEquals("Also add a regression test", run.prompt)
        assertEquals(run.id, repository.agent(agent.id).summary.latestRunId)
        assertEquals(AgentStatus.ACTIVE, repository.agent(agent.id).summary.status)
    }

    @Test
    fun `launching an agent adds it to the top of the inbox`() = runBlocking {
        val (detail, run) = repository.createAgent(
            CreateAgentRequest(
                prompt = "Split the billing module",
                repoUrl = "https://github.com/acme/storefront",
                startingRef = "main",
                modelId = "composer-2",
            ),
        )

        assertEquals("Split the billing module", detail.summary.name)
        assertEquals(detail.summary.id, repository.agents().items.first().id)
        assertEquals(listOf(run), repository.runs(detail.summary.id))
    }

    @Test
    fun `deleting an agent drops its runs too`() = runBlocking {
        val agent = repository.agents().items.first()

        repository.delete(agent.id)

        assertTrue(repository.agents(includeArchived = true).items.none { it.id == agent.id })
        assertTrue(repository.runs(agent.id).isEmpty())
    }

    @Test
    fun `archiving moves an agent out of the default list`() = runBlocking {
        val agent = repository.agents().items.first()

        repository.setArchived(agent.id, archived = true)

        assertTrue(repository.agents().items.none { it.id == agent.id })
        assertEquals(AgentStatus.ARCHIVED, repository.agent(agent.id).summary.status)
    }
}
