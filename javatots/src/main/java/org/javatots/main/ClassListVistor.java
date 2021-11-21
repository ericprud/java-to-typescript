package org.javatots.main;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Set;

/**
 * collect names of ClassOrInterfaceTypes; filter with @intersectWith if non-null
 */
public class ClassListVistor extends VoidVisitorAdapter<Set<String>> {
    Set<String> intersectWith;

    public ClassListVistor(final Set<String> intersectWith) {
        this.intersectWith = intersectWith;
    }

    @Override
    public void visit(final ClassOrInterfaceType n, final Set<String> collector) {
        final String className = n.getName().asString();
        if (this.intersectWith == null || this.intersectWith.contains(className)) {
            collector.add(className);
        }
        super.visit(n, collector);
    }
}

