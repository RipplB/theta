package hu.bme.mit.theta.xsts

import com.google.common.base.Preconditions.checkArgument
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.SkipStmt
import hu.bme.mit.theta.core.stmt.Stmt

fun normalize(rawXsts: XSTS?) : XSTS {
    checkArgument(rawXsts != null, "Can't normalize null")
    val xstsInput = rawXsts!!

    val normalizedInit = normalize(xstsInput.init)
    val normalizedTran = normalize(xstsInput.tran)
    val normalizedEnv = normalize(xstsInput.env)

    return XSTS(
            xstsInput.ctrlVars,
            normalizedInit,
            normalizedTran,
            normalizedEnv,
            xstsInput.initFormula,
            xstsInput.prop
    )
}



private fun normalize(stmt: Stmt): NonDetStmt {
    val collector = mutableListOf<MutableList<Stmt>>()
    collector.add(mutableListOf())
    normalize(stmt, collector)
    return NonDetStmt.of(collector.map { SequenceStmt.of(it) }.toList())
}

private fun normalize(stmt: Stmt, collector: MutableList<MutableList<Stmt>>) {
    when (stmt) {
        is SequenceStmt -> stmt.stmts.forEach { normalize(it, collector) }
        is NonDetStmt -> {
            val newCollector = mutableListOf<MutableList<Stmt>>()
            stmt.stmts.forEach { nondetBranch ->
                val copy = collector.copy()
                normalize(nondetBranch, copy)
                newCollector.addAll(copy)
            }
            collector.clear()
            collector.addAll(newCollector)
        }

        is SkipStmt -> {}
        else -> collector.forEach { it.add(stmt) }
    }
}

private fun MutableList<MutableList<Stmt>>.copy() = map { it.toMutableList() }.toMutableList()