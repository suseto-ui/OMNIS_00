package com.example.ui.dialectics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dialectics.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArenaViewModel : ViewModel() {
    private val _currentArenaMode = MutableStateFlow(ArenaMode.FOUR_AGENTS)
    val currentArenaMode = _currentArenaMode.asStateFlow()

    // 4-Agent Debate State
    val fourAgentProblemText = MutableStateFlow(AgentArenaLibrary.presetDebateTopics.first())
    val isDebating4A = MutableStateFlow(false)
    val liveTurns4A = MutableStateFlow<List<ArenaDebateTurn>>(emptyList())
    val fourAgentResult = MutableStateFlow<FourAgentDebateResult?>(null)

    // 2-Agent Duel State
    val selectedAgentA = MutableStateFlow(AgentArenaLibrary.ARCHITECT)
    val selectedAgentB = MutableStateFlow(AgentArenaLibrary.REGULATOR)
    val problemText2A = MutableStateFlow(AgentArenaLibrary.presetDebateTopics.first())
    val isDebating2A = MutableStateFlow(false)
    val debateResult2A = MutableStateFlow<ArenaDebateResult?>(null)
    val liveTurns2A = MutableStateFlow<List<ArenaDebateTurn>>(emptyList())
    val comparePair = MutableStateFlow<Pair<ArenaDebateResult, ArenaDebateResult>?>(null)
    val overrideFlagInput = MutableStateFlow("")
    val showOverrideConfirmDialog = MutableStateFlow(false)
    val historyList2A = AgentArenaEngine.debateHistoryState

    fun setOverrideFlagInput(input: String) { overrideFlagInput.value = input }
    fun setShowOverrideConfirmDialog(show: Boolean) { showOverrideConfirmDialog.value = show }
    fun setComparePair(pair: Pair<ArenaDebateResult, ArenaDebateResult>?) { comparePair.value = pair }

    fun setSelectedAgentA(agent: AgentArchetype) {
        selectedAgentA.value = agent
    }

    fun setSelectedAgentB(agent: AgentArchetype) {
        selectedAgentB.value = agent
    }
    
    fun setLiveTurns2A(turns: List<ArenaDebateTurn>) {
        liveTurns2A.value = turns
    }

    fun setDebateResult2A(result: ArenaDebateResult?) {
        debateResult2A.value = result
    }

    fun setArenaMode(mode: ArenaMode) {
        _currentArenaMode.value = mode
    }

    fun setFourAgentProblemText(text: String) {
        fourAgentProblemText.value = text
    }

    fun setProblemText2A(text: String) {
        problemText2A.value = text
    }

    fun applyExecutiveOverride(debateId: String, flag: String) {
        val updated = AgentArenaEngine.applyExecutiveOverride(debateId, flag)
        if (updated != null) {
            fourAgentResult.value = updated
        }
    }

    fun startFourAgentDebate(problemText: String) {
        if (isDebating4A.value) return
        isDebating4A.value = true
        liveTurns4A.value = emptyList()
        fourAgentResult.value = null

        viewModelScope.launch {
            val result = AgentArenaEngine.executeFourAgentDebate(
                problemStatement = problemText,
                onProgressUpdate = { newTurn ->
                    liveTurns4A.value = liveTurns4A.value + newTurn
                }
            )
            fourAgentResult.value = result
            isDebating4A.value = false
        }
    }

    fun startTwoAgentDebate(problemText: String, agentA: AgentArchetype, agentB: AgentArchetype) {
        if (isDebating2A.value) return
        isDebating2A.value = true
        liveTurns2A.value = emptyList()
        debateResult2A.value = null

        viewModelScope.launch {
            val result = AgentArenaEngine.executeArenaDebate(
                problemStatement = problemText,
                agentA = agentA,
                agentB = agentB,
                onProgressUpdate = { newTurn ->
                    liveTurns2A.value = liveTurns2A.value + newTurn
                }
            )
            debateResult2A.value = result
            isDebating2A.value = false

            result.synthesized8DScores.forEach { (domain, score) ->
                com.example.engine.AkgeTensorKernel.updateDomainWeight(domain, score)
            }
        }
    }
}
