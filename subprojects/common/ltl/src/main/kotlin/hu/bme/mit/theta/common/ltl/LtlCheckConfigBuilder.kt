package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.Analysis
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState

class LtlCheckConfigBuilder<RState : ExprState, RBlank : ExprState, RAction : ExprAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState>
    (analysis: Analysis<RState, RAction, RPrec>, lts: LTS<in RState, RAction>){
    companion object {
        @JvmStatic
        fun <RState : ExprState, RBlank : ExprState, RAction : ExprAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState>
                create(analysis: Analysis<RState, RAction, RPrec>, lts: LTS<in RState, RAction>) : LtlCheckConfigBuilder<RState, RBlank, RAction, RPrec, RBlankPrec, DataPrec, DataState> {
                    return LtlCheckConfigBuilder(analysis, lts)
                }
    }



}