package org.rust.lang.core.type.unresolved

import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedTupleType(val elements: Iterable<RustUnresolvedType>) : RustUnresolvedType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitTupleType(this)

}
