// Copyright (c) 2015-2018 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.inject.Provider;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.FormulaContext;
import org.kframework.backend.java.util.Z3Wrapper;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.options.SMTOptions;
import org.kframework.utils.options.SMTSolver;

import java.util.Set;

public class SMTOperations {

    private final SMTOptions        smtOptions;
    private final Z3Wrapper         z3;
    private final GlobalOptions     global;
    private final KExceptionManager kem;

    public SMTOperations(
            Provider<Definition> definitionProvider,
            SMTOptions smtOptions,
            Z3Wrapper z3,
            KExceptionManager kem,
            GlobalOptions global) {
        this.smtOptions = smtOptions;
        this.z3         = z3;
        this.kem        = kem;
        this.global     = global;
    }

    public boolean checkUnsat(ConjunctiveFormula constraint) {
        if (smtOptions.smt != SMTSolver.Z3) {
            return false;
        }

        if (constraint.isSubstitution()) {
            return false;
        }

        boolean result = false;
        try {
            constraint.globalContext().profiler.queryBuildTimer.start();
            CharSequence query;
            try {
                query = KILtoSMTLib.translateConstraint(constraint).toString();
            } finally {
                constraint.globalContext().profiler.queryBuildTimer.stop();
            }
            if (global.debugFull) {
                System.err.format("\nAttempting to check unsat for:\n================= \n\t%s\n" +
                        "query: \n\t%s\n", constraint, query);
            }
            result = z3.isUnsat(query, smtOptions.z3CnstrTimeout, constraint.globalContext().profiler.z3Constraint);
            if (result && RuleAuditing.isAuditBegun()) {
                System.err.format("SMT query returned unsat: %s\n", query);
            }
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Checks if {@code left => right}, or {@code left /\ !right} is unsat.
     */
    public boolean impliesSMT(
            ConjunctiveFormula left,
            ConjunctiveFormula right,
            Set<Variable> rightOnlyVariables, FormulaContext formulaContext) {
        if (smtOptions.smt == SMTSolver.Z3) {
            try {
                //From this point on, will be converted to toString() anyway.
                left.globalContext().profiler.queryBuildTimer.start();
                CharSequence query;
                try {
                    query = KILtoSMTLib.translateImplication(left, right, rightOnlyVariables).toString();
                } finally {
                    left.globalContext().profiler.queryBuildTimer.stop();
                }
                if (global.debugZ3Queries) {
                    System.err.format("\nZ3 query:\n%s\n", query);
                }
                return z3.isUnsat(query, smtOptions.z3ImplTimeout, formulaContext.z3Profiler);
            } catch (UnsupportedOperationException | SMTTranslationFailure e) {
                if (!smtOptions.ignoreMissingSMTLibWarning) {
                    kem.registerCriticalWarning(e.getMessage(), e);
                }
                if (global.debugZ3) {
                    System.err.format("\nZ3 warning: %s\n", e.getMessage());
                }
            }
        }
        return false;
    }
}
