package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.k2.ClassIds.KTOR_RESPONDS_NO_OP
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Drops any call to
 * RouteContext.responds<T>() so it's not included in the bytecode to avoid any overhead.
 */
class ResponseSchemaIrLoweringExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    if (call.symbol.owner.fqNameWhenAvailable == KTOR_RESPONDS_NO_OP) {
                        return IrBlockImpl(
                            call.startOffset, call.endOffset,
                            pluginContext.irBuiltIns.unitType, null, emptyList()
                        )
                    }
                    return call
                }
            }
        )
    }
}