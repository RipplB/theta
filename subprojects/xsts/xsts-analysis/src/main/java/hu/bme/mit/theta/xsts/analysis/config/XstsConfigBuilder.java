/*
 *  Copyright 2023 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package hu.bme.mit.theta.xsts.analysis.config;

import hu.bme.mit.theta.analysis.*;
import hu.bme.mit.theta.analysis.algorithm.ArgBuilder;
import hu.bme.mit.theta.analysis.algorithm.ArgNodeComparators;
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker;
import hu.bme.mit.theta.analysis.algorithm.cegar.Abstractor;
import hu.bme.mit.theta.analysis.algorithm.cegar.BasicAbstractor;
import hu.bme.mit.theta.analysis.algorithm.cegar.CegarChecker;
import hu.bme.mit.theta.analysis.algorithm.cegar.Refiner;
import hu.bme.mit.theta.analysis.algorithm.cegar.abstractor.StopCriterions;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy;
import hu.bme.mit.theta.analysis.expl.*;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprStatePredicate;
import hu.bme.mit.theta.analysis.expr.StmtAction;
import hu.bme.mit.theta.analysis.expr.refinement.*;
import hu.bme.mit.theta.analysis.pred.*;
import hu.bme.mit.theta.analysis.prod2.Prod2Analysis;
import hu.bme.mit.theta.analysis.prod2.Prod2Prec;
import hu.bme.mit.theta.analysis.prod2.Prod2State;
import hu.bme.mit.theta.analysis.prod2.prod2explpred.*;
import hu.bme.mit.theta.analysis.stmtoptimizer.DefaultStmtOptimizer;
import hu.bme.mit.theta.analysis.unit.UnitState;
import hu.bme.mit.theta.analysis.waitlist.PriorityWaitlist;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.logging.NullLogger;
import hu.bme.mit.theta.common.ltl.LtlCheck;
import hu.bme.mit.theta.common.ltl.LtlChecker;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.SolverFactory;
import hu.bme.mit.theta.xsts.XSTS;
import hu.bme.mit.theta.xsts.analysis.*;
import hu.bme.mit.theta.xsts.analysis.autoexpl.XstsAutoExpl;
import hu.bme.mit.theta.xsts.analysis.autoexpl.XstsNewAtomsAutoExpl;
import hu.bme.mit.theta.xsts.analysis.autoexpl.XstsNewOperandsAutoExpl;
import hu.bme.mit.theta.xsts.analysis.autoexpl.XstsStaticAutoExpl;
import hu.bme.mit.theta.xsts.analysis.initprec.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.function.Predicate;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Not;

public class XstsConfigBuilder {

    public enum Domain {
        EXPL, PRED_BOOL, PRED_CART, PRED_SPLIT, EXPL_PRED_BOOL, EXPL_PRED_CART, EXPL_PRED_SPLIT, EXPL_PRED_COMBINED
    }

    public enum Refinement {
        FW_BIN_ITP, BW_BIN_ITP, SEQ_ITP, MULTI_SEQ, UNSAT_CORE
    }

    public enum Search {
        BFS(ArgNodeComparators.combine(ArgNodeComparators.targetFirst(), ArgNodeComparators.bfs())),

        DFS(ArgNodeComparators.combine(ArgNodeComparators.targetFirst(), ArgNodeComparators.dfs()));

        public final ArgNodeComparators.ArgNodeComparator comparator;

        private Search(final ArgNodeComparators.ArgNodeComparator comparator) {
            this.comparator = comparator;
        }

    }

    public enum PredSplit {
        WHOLE(ExprSplitters.whole()),

        CONJUNCTS(ExprSplitters.conjuncts()),

        ATOMS(ExprSplitters.atoms());

        public final ExprSplitters.ExprSplitter splitter;

        private PredSplit(final ExprSplitters.ExprSplitter splitter) {
            this.splitter = splitter;
        }
    }

    public enum InitPrec {
        EMPTY(new XstsEmptyInitPrec()),

        PROP(new XstsPropInitPrec()),

        CTRL(new XstsCtrlInitPrec()),

        ALLVARS(new XstsAllVarsInitPrec());

        public final XstsInitPrec builder;

        private InitPrec(final XstsInitPrec builder) {
            this.builder = builder;
        }

    }

    public enum AutoExpl {
        STATIC(new XstsStaticAutoExpl()),

        NEWATOMS(new XstsNewAtomsAutoExpl()),

        NEWOPERANDS(new XstsNewOperandsAutoExpl());

        public final XstsAutoExpl builder;

        private AutoExpl(final XstsAutoExpl builder) {
            this.builder = builder;
        }
    }

    public enum OptimizeStmts {
        ON, OFF
    }

    private Logger logger = NullLogger.getInstance();
    private final SolverFactory abstractionSolverFactory;
    private final SolverFactory refinementSolverFactory;
    private final Domain domain;
    private final Refinement refinement;
    private RefinerStrategy refinerStrategy = RefinerStrategy.defaultValue();
    private Search search = Search.BFS;
    private SearchStrategy searchStrategy = SearchStrategy.DFS;
    private PredSplit predSplit = PredSplit.WHOLE;
    private int maxEnum = 0;
    private InitPrec initPrec = InitPrec.EMPTY;
    private PruneStrategy pruneStrategy = PruneStrategy.LAZY;
    private OptimizeStmts optimizeStmts = OptimizeStmts.ON;
    private AutoExpl autoExpl = AutoExpl.NEWOPERANDS;
    private String ltl;
    private boolean isLtl = false;

    public XstsConfigBuilder(final Domain domain, final Refinement refinement,
                             final SolverFactory abstractionSolverFactory, final SolverFactory refinementSolverFactory) {
        this.domain = domain;
        this.refinement = refinement;
        this.abstractionSolverFactory = abstractionSolverFactory;
        this.refinementSolverFactory = refinementSolverFactory;
    }

    public XstsConfigBuilder logger(final Logger logger) {
        this.logger = logger;
        return this;
    }

    public XstsConfigBuilder search(final Search search) {
        this.search = search;
        return this;
    }

    public XstsConfigBuilder predSplit(final PredSplit predSplit) {
        this.predSplit = predSplit;
        return this;
    }

    public XstsConfigBuilder maxEnum(final int maxEnum) {
        this.maxEnum = maxEnum;
        return this;
    }

    public XstsConfigBuilder initPrec(final InitPrec initPrec) {
        this.initPrec = initPrec;
        return this;
    }

    public XstsConfigBuilder pruneStrategy(final PruneStrategy pruneStrategy) {
        this.pruneStrategy = pruneStrategy;
        return this;
    }

    public XstsConfigBuilder optimizeStmts(final OptimizeStmts optimizeStmts) {
        this.optimizeStmts = optimizeStmts;
        return this;
    }

    public XstsConfigBuilder autoExpl(final AutoExpl autoExpl) {
        this.autoExpl = autoExpl;
        return this;
    }

    public XstsConfigBuilder ltl(final boolean isLtl, final String ltl) {
        this.isLtl = isLtl;
        this.ltl = ltl;
        return this;
    }

    public XstsConfigBuilder refinerStrategy(final RefinerStrategy refinerStrategy) {
        this.refinerStrategy = refinerStrategy;
        return this;
    }

    public XstsConfigBuilder searchStrategy(final SearchStrategy searchStrategy) {
        this.searchStrategy = searchStrategy;
        return this;
    }

    public XstsConfig<? extends State, ? extends Action, ? extends Prec> build(final XSTS xsts) {
        final Solver abstractionSolver = abstractionSolverFactory.createSolver();
        final Expr<BoolType> negProp = Not(xsts.getProp());

        ExplStmtAnalysis explStmtAnalysis = ExplStmtAnalysis.create(abstractionSolver, xsts.getInitFormula(), maxEnum);
        return switch (domain) {
            case EXPL -> getExplConfig(xsts, abstractionSolver, negProp, explStmtAnalysis);
            case PRED_BOOL, PRED_CART, PRED_SPLIT -> getPredConfig(xsts, abstractionSolver, negProp);
            case EXPL_PRED_BOOL, EXPL_PRED_CART, EXPL_PRED_SPLIT, EXPL_PRED_COMBINED -> getExplPredConfig(xsts, abstractionSolver, negProp, explStmtAnalysis);
        };
    }

    @NotNull
    private XstsConfig<XstsState<ExplState>, XstsAction, ExplPrec> getExplConfig(XSTS xsts, Solver abstractionSolver, Expr<BoolType> negProp, ExplStmtAnalysis explStmtAnalysis) {
        final LTS<XstsState<ExplState>, XstsAction> lts;
        if (optimizeStmts == OptimizeStmts.ON) {
            lts = XstsLts.create(xsts,
                    XstsStmtOptimizer.create(ExplStmtOptimizer.getInstance()));
        } else {
            lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create()));
        }

        final Predicate<XstsState<ExplState>> target = new XstsStatePredicate<>(
                new ExplStatePredicate(negProp, abstractionSolver));
        final Analysis<XstsState<ExplState>, XstsAction, ExplPrec> analysis = XstsAnalysis.create(
                explStmtAnalysis);
        final ArgBuilder<XstsState<ExplState>, XstsAction, ExplPrec> argBuilder = ArgBuilder.create(
                lts, analysis, target,
                true);
        final Abstractor<XstsState<ExplState>, XstsAction, ExplPrec> abstractor = BasicAbstractor.builder(
                        argBuilder)
                .waitlist(PriorityWaitlist.create(search.comparator))
                .stopCriterion(refinement == Refinement.MULTI_SEQ ? StopCriterions.fullExploration()
                        : StopCriterions.firstCex())
                .logger(logger).build();

        Refiner<XstsState<ExplState>, XstsAction, ExplPrec> refiner = switch (refinement) {
            case FW_BIN_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceFwBinItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(new ItpRefToExplPrec()), pruneStrategy, logger);
            case BW_BIN_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceBwBinItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(new ItpRefToExplPrec()), pruneStrategy, logger);
            case SEQ_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceSeqItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(new ItpRefToExplPrec()), pruneStrategy, logger);
            case MULTI_SEQ -> MultiExprTraceRefiner.create(
                    ExprTraceSeqItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(new ItpRefToExplPrec()), pruneStrategy, logger);
            case UNSAT_CORE -> SingleExprTraceRefiner.create(
                    ExprTraceUnsatCoreChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createUCSolver()),
                    JoiningPrecRefiner.create(new VarsRefToExplPrec()), pruneStrategy, logger);
            default -> throw new UnsupportedOperationException(
                    domain + " domain does not support " + refinement + " refinement.");
        };

        final ExplPrec prec = initPrec.builder.createExpl(xsts);
        final SafetyChecker<XstsState<ExplState>, XstsAction, ExplPrec> checker = isLtl ? new LtlChecker<>(
                analysis,
                lts,
                new ItpRefToExplPrec(),
                explStmtAnalysis,
                new ItpRefToExplPrec(),
                XstsInitFunc.create(p -> Collections.singletonList(UnitState.getInstance())),
                xsts.getInitFormula(),
                prec,
                xsts.getVars(),
                ((unit, data) -> XstsState.of(data, unit.lastActionWasEnv(), unit.isInitialized())),
                (state -> XstsState.of(UnitState.getInstance(), state.lastActionWasEnv(), state.isInitialized())),
                XstsState::getState,
                (p -> p),
                LtlCheck::alternatingNextSide,
                ltl,
                refinementSolverFactory.createItpSolver(),
                searchStrategy,
                refinerStrategy,
                logger
        ) : CegarChecker.create(
                abstractor,
                refiner,
                logger);
        return XstsConfig.create(checker, prec);
    }

    @NotNull
    private XstsConfig<XstsState<PredState>, XstsAction, PredPrec> getPredConfig(XSTS xsts, Solver abstractionSolver, Expr<BoolType> negProp) {
        PredAbstractors.PredAbstractor predAbstractor = getPredAbstractor(abstractionSolver);

        final LTS<XstsState<PredState>, XstsAction> lts;
        if (optimizeStmts == OptimizeStmts.ON) {
            lts = XstsLts.create(xsts,
                    XstsStmtOptimizer.create(PredStmtOptimizer.getInstance()));
        } else {
            lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create()));
        }

        final Predicate<XstsState<PredState>> target = new XstsStatePredicate<>(
                new ExprStatePredicate(negProp, abstractionSolver));
        PredAnalysis<ExprAction> predAnalysis = PredAnalysis.create(abstractionSolver, predAbstractor,
                xsts.getInitFormula());
        final Analysis<XstsState<PredState>, XstsAction, PredPrec> analysis = XstsAnalysis.create(
                predAnalysis);
        final ArgBuilder<XstsState<PredState>, XstsAction, PredPrec> argBuilder = ArgBuilder.create(
                lts, analysis, target,
                true);
        final Abstractor<XstsState<PredState>, XstsAction, PredPrec> abstractor = BasicAbstractor.builder(
                        argBuilder)
                .waitlist(PriorityWaitlist.create(search.comparator))
                .stopCriterion(refinement == Refinement.MULTI_SEQ ? StopCriterions.fullExploration()
                        : StopCriterions.firstCex())
                .logger(logger).build();

        ExprTraceChecker<ItpRefutation> exprTraceChecker = switch (refinement) {
            case FW_BIN_ITP -> ExprTraceFwBinItpChecker.create(xsts.getInitFormula(),
                    negProp, refinementSolverFactory.createItpSolver());
            case BW_BIN_ITP -> ExprTraceBwBinItpChecker.create(xsts.getInitFormula(),
                    negProp, refinementSolverFactory.createItpSolver());
            case SEQ_ITP, MULTI_SEQ -> ExprTraceSeqItpChecker.create(xsts.getInitFormula(), negProp,
                    refinementSolverFactory.createItpSolver());
            default -> throw new UnsupportedOperationException(
                    domain + " domain does not support " + refinement + " refinement.");
        };
        Refiner<XstsState<PredState>, XstsAction, PredPrec> refiner;
        ItpRefToPredPrec refToPrec = new ItpRefToPredPrec(predSplit.splitter);
        if (refinement == Refinement.MULTI_SEQ) {
            refiner = MultiExprTraceRefiner.create(exprTraceChecker,
                    JoiningPrecRefiner.create(refToPrec),
                    pruneStrategy, logger);
        } else {
            refiner = SingleExprTraceRefiner.create(exprTraceChecker,
                    JoiningPrecRefiner.create(refToPrec),
                    pruneStrategy, logger);
        }

        final PredPrec prec = initPrec.builder.createPred(xsts);
        final SafetyChecker<XstsState<PredState>, XstsAction, PredPrec> checker = isLtl ? new LtlChecker<>(
                analysis,
                lts,
                refToPrec,
                predAnalysis,
                refToPrec,
                XstsInitFunc.create(p -> Collections.singletonList(UnitState.getInstance())),
                xsts.getInitFormula(),
                prec,
                xsts.getVars(),
                ((unit, data) -> XstsState.of(data, unit.lastActionWasEnv(), unit.isInitialized())),
                (state -> XstsState.of(UnitState.getInstance(), state.lastActionWasEnv(), state.isInitialized())),
                XstsState::getState,
                (p -> p),
                LtlCheck::alternatingNextSide,
                ltl,
                refinementSolverFactory.createItpSolver(),
                searchStrategy,
                refinerStrategy,
                logger
        ) : CegarChecker.create(
                abstractor,
                refiner,
                logger);

        return XstsConfig.create(checker, prec);
    }

    @NotNull
    private XstsConfig<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> getExplPredConfig(XSTS xsts, Solver abstractionSolver, Expr<BoolType> negProp, ExplStmtAnalysis explStmtAnalysis) {
        final LTS<XstsState<Prod2State<ExplState, PredState>>, XstsAction> lts;
        if (optimizeStmts == OptimizeStmts.ON) {
            lts = XstsLts.create(xsts, XstsStmtOptimizer.create(
                    Prod2ExplPredStmtOptimizer.create(
                            ExplStmtOptimizer.getInstance()
                    )));
        } else {
            lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create()));
        }

        final Analysis<Prod2State<ExplState, PredState>, StmtAction, Prod2Prec<ExplPrec, PredPrec>> prod2Analysis;
        final Predicate<XstsState<Prod2State<ExplState, PredState>>> target = new XstsStatePredicate<>(
                new ExprStatePredicate(negProp, abstractionSolver));
        if (domain == Domain.EXPL_PRED_BOOL || domain == Domain.EXPL_PRED_CART
                || domain == Domain.EXPL_PRED_SPLIT) {
            final PredAbstractors.PredAbstractor predAbstractor = getPredAbstractor(abstractionSolver);
            prod2Analysis = Prod2Analysis.create(
                    explStmtAnalysis,
                    PredAnalysis.create(abstractionSolver, predAbstractor, xsts.getInitFormula()),
                    Prod2ExplPredPreStrengtheningOperator.create(),
                    Prod2ExplPredStrengtheningOperator.create(abstractionSolver));
        } else {
            final Prod2ExplPredAbstractors.Prod2ExplPredAbstractor prodAbstractor = Prod2ExplPredAbstractors.booleanAbstractor(
                    abstractionSolver);
            prod2Analysis = Prod2ExplPredAnalysis.create(
                    ExplAnalysis.create(abstractionSolver, xsts.getInitFormula()),
                    PredAnalysis.create(abstractionSolver,
                            PredAbstractors.booleanAbstractor(abstractionSolver),
                            xsts.getInitFormula()),
                    Prod2ExplPredStrengtheningOperator.create(abstractionSolver),
                    prodAbstractor);
        }
        final Analysis<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> analysis = XstsAnalysis.create(
                prod2Analysis);

        final ArgBuilder<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> argBuilder = ArgBuilder.create(
                lts, analysis, target,
                true);
        final Abstractor<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> abstractor = BasicAbstractor.builder(
                        argBuilder)
                .waitlist(PriorityWaitlist.create(search.comparator))
                .stopCriterion(refinement == Refinement.MULTI_SEQ ? StopCriterions.fullExploration()
                        : StopCriterions.firstCex())
                .logger(logger).build();

        final RefutationToPrec<Prod2Prec<ExplPrec, PredPrec>, ItpRefutation> precRefiner = AutomaticItpRefToProd2ExplPredPrec.create(
                autoExpl.builder.create(xsts), predSplit.splitter);

        Refiner<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> refiner
                = switch (refinement) {
            case FW_BIN_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceFwBinItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(precRefiner), pruneStrategy, logger);
            case BW_BIN_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceBwBinItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(precRefiner), pruneStrategy, logger);
            case SEQ_ITP -> SingleExprTraceRefiner.create(
                    ExprTraceSeqItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(precRefiner), pruneStrategy, logger);
            case MULTI_SEQ -> MultiExprTraceRefiner.create(
                    ExprTraceSeqItpChecker.create(xsts.getInitFormula(), negProp,
                            refinementSolverFactory.createItpSolver()),
                    JoiningPrecRefiner.create(precRefiner), pruneStrategy, logger);
            default -> throw new UnsupportedOperationException(
                    domain + " domain does not support " + refinement + " refinement.");
        };

        final Prod2Prec<ExplPrec, PredPrec> prec = initPrec.builder.createProd2ExplPred(xsts);
        final SafetyChecker<XstsState<Prod2State<ExplState, PredState>>, XstsAction, Prod2Prec<ExplPrec, PredPrec>> checker = isLtl ? new LtlChecker<>(
                analysis,
                lts,
                precRefiner,
                prod2Analysis,
                precRefiner,
                XstsInitFunc.create(p -> Collections.singletonList(UnitState.getInstance())),
                xsts.getInitFormula(),
                prec,
                xsts.getVars(),
                ((unit, data) -> XstsState.of(data, unit.lastActionWasEnv(), unit.isInitialized())),
                (state -> XstsState.of(UnitState.getInstance(), state.lastActionWasEnv(), state.isInitialized())),
                XstsState::getState,
                (p -> p),
                LtlCheck::alternatingNextSide,
                ltl,
                refinementSolverFactory.createItpSolver(),
                searchStrategy,
                refinerStrategy,
                logger
        ) : CegarChecker.create(
                abstractor,
                refiner,
                logger);
        return XstsConfig.create(checker, prec);
    }

    @NotNull
    private PredAbstractors.PredAbstractor getPredAbstractor(Solver abstractionSolver) {
        return switch (domain) {
            case PRED_BOOL -> PredAbstractors.booleanAbstractor(abstractionSolver);
            case PRED_SPLIT -> PredAbstractors.booleanSplitAbstractor(abstractionSolver);
            case PRED_CART -> PredAbstractors.cartesianAbstractor(abstractionSolver);
            default -> throw new UnsupportedOperationException(domain + " domain is not supported.");
        };
    }


}