package com.epam.drill.agent.classloading

import java.util.*

fun <T> Iterable<T>.leaves(parentOf: (T) -> T?) = fold(Leaves(parentOf), Leaves<T>::plus)

fun <T> T.parents(parentOf: (T) -> T?): List<T> = parentOf(this)?.run {
    parents(parentOf) + this
} ?: emptyList()

fun <T> mutRefSet(): MutableSet<T> = IdentityHashMap<T, Unit>().run {
    object : MutableSet<T> by keys {
        private val ordered = mutableListOf<T>()

        override fun addAll(elements: Collection<T>): Boolean = elements.fold(true) { acc, elem ->
            add(elem) && acc
        }

        override fun add(element: T) = (put(element, Unit) == null).also {
            if (it) ordered.add(element)
        }

        override fun iterator(): MutableIterator<T> = ordered.asSequence().filter { it in keys }.iterator().let {
            object : MutableIterator<T> {
                override fun hasNext(): Boolean = it.hasNext()

                override fun next(): T = it.next()

                override fun remove()  = Unit
            }
        }
    }
}

class Leaves<T>(private val parentOf: (T) -> T?) {
    private val parents = mutRefSet<T>()

    private val leaves = mutRefSet<T>()

    private val T.parents: List<T> get() = parentOf(this)?.run {
        listOf(this) + parents
    } ?: emptyList()

    operator fun plus(elem: T): Leaves<T> {
        if (elem !in parents && elem !in leaves) {
            elem.parents.forEach { parent->
                if (parent in leaves) {
                    leaves.remove(parent)
                }
                parents.add(parent)
            }
            leaves.add(elem)
        }
        return this
    }

    fun toListWith(node: T) = toList() + when(node) {
        !in leaves, !in parents -> listOf(node)
        else -> emptyList()
    }

    fun toList() = leaves.toList()
}
