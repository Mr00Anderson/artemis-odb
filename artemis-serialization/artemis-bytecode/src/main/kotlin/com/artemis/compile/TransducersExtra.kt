package com.artemis.compile

import net.onedaybeard.transducers.ReducingFunction
import net.onedaybeard.transducers.Transducer
import net.onedaybeard.transducers.transduce
import java.util.concurrent.atomic.AtomicBoolean


@Suppress("UNCHECKED_CAST")
fun <R : MutableList<A>, A, B> intoList(xf: Transducer<A, B>,
                                        init: R = mutableListOf<A>() as R,
                                        input: Iterable<B>): R {
    return transduce(xf, object : ReducingFunction<R, A> {
        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R {
            result.add(input)
            return result
        }
    }, init, input)
}


@Suppress("UNUSED")
class Key<K> // <-- hah! type infer it below

@Suppress("UNCHECKED_CAST")
fun <R, A, B, K> intoMap(xf: Transducer<A, B>,
                         init: R = mutableMapOf<K, A>() as R,
                         keyType: Key<K> = Key<Any>() as Key<K>,
                         input: Iterable<B>): R
        where R : Map<K, A> {

    val rf = object : ReducingFunction<R, A> {
        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R = result
    }

    return transduce(xf, rf, init, input)
}

inline fun <reified A : Any, B> subduce(xf: Transducer<A, B>): ReducingFunction<A, B> {
    val rf: ReducingFunction<A, A> = object : ReducingFunction<A, A> {
        override fun apply(): A {
            return when (A::class) {
                Boolean::class   -> false as A
                Byte::class      -> 0 as A
                Character::class -> ' ' as A
                Short::class     -> 0 as A
                Int::class       -> 0 as A
                Long::class      -> 0 as A
                Float::class     -> 0 as A
                Double::class    -> 0 as A
                else             -> A::class.java.newInstance()
            }
        }

        override fun apply(result: A,
                           input: A,
                           reduced: AtomicBoolean): A = input
    }

    return xf.apply(rf)
}


inline fun <A, reified K, reified V> makePair(left: Transducer<K, A>,
                                              right: Transducer<V, A>): Transducer<Pair<K, V>, A>
        where K : Any, V : Any {

    return object : Transducer<Pair<K, V>, A> {
        val leftRf: ReducingFunction<K, A> = subduce(left)
        val rightRf: ReducingFunction<V, A> = subduce(right)

        override fun <R> apply(rf: ReducingFunction<R, Pair<K, V>>): ReducingFunction<R, A> {
            return object : ReducingFunction<R, A> {
                override fun apply(result: R,
                                   input: A,
                                   reduced: AtomicBoolean): R {

                    val l = leftRf.apply(leftRf.apply(), input, reduced)
                    val r = rightRf.apply(rightRf.apply(), input, reduced)

                    return rf.apply(result, Pair(l, r), reduced)
                }
            }
        }
    }
}


// @todo collect to (array/map)-of-(list/set/bitset)
inline fun <K, V> groupBy() : Transducer<Map<K, Iterable<V>>, Pair<K, V>> {
    return object : Transducer<Map<K, Iterable<V>>, Pair<K, V>> {
        override fun <R> apply(rf: ReducingFunction<R, Map<K, Iterable<V>>>): ReducingFunction<R, Pair<K, V>> {
            return object : ReducingFunction<R, Pair<K, V>> {
                override fun apply(result: R,
                                   input: Pair<K, V>,
                                   reduced: AtomicBoolean): R {

                    val key = input.first
                    val group = result as MutableMap<K, MutableList<V>>
                    val list = group[key] ?: mutableListOf<V>().apply { group[key] = this }
                    list += input.second

                    return result
                }
            }
        }
    }
}

fun <A, K> groupBy(f: (A) -> K) = object : Transducer<Map<K, Iterable<A>>, A> {
    override fun <R> apply(rf: ReducingFunction<R, Map<K, Iterable<A>>>): ReducingFunction<R, A> {
        return object : ReducingFunction<R, A> {
            override fun apply(): R = rf.apply()

            override fun apply(result: R,
                               input: A,
                               reduced: AtomicBoolean): R {

                val key: K = f(input)

                @Suppress("UNCHECKED_CAST")
                val group = result as MutableMap<K, MutableList<A>>
                val list = group[key] ?: mutableListOf<A>().apply { group[key] = this }
                list += input

                return result
            }
        }
    }
}


operator fun <A, B, C> Transducer<B, C>.plus(right: Transducer<A, in B>): Transducer<A, C>
        = this.comp(right)