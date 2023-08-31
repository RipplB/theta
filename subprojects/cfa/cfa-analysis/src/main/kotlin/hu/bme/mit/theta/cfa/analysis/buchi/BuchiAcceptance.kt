package hu.bme.mit.theta.cfa.analysis.buchi

import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState

object BuchiAcceptance {

    @JvmStatic
    fun <S: ExprState> transitionBased(buchiAutomaton: CFA) : AcceptancePredicate<CfaState<S>, CfaAction> {
        return AcceptancePredicate.ofActionPredicate { action -> action.edges.any { edge -> buchiAutomaton.acceptingEdges.contains(edge) } }
    }

}