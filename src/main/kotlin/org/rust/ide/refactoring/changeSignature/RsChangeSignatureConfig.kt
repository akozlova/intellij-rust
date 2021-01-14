/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER
import org.rust.ide.refactoring.RsFunctionSignatureConfig
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.isAsync
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

/**
 * This class just holds [config].
 * It is required by [com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase].
 */
class RsSignatureChangeInfo(val config: RsChangeFunctionSignatureConfig) : ChangeInfo {
    override fun getNewParameters(): Array<ParameterInfo> = arrayOf()
    override fun isParameterSetOrOrderChanged(): Boolean = false
    override fun isParameterTypesChanged(): Boolean = false
    override fun isParameterNamesChanged(): Boolean = false
    override fun isGenerateDelegate(): Boolean = false

    override fun isReturnTypeChanged(): Boolean = config.returnTypeDisplay?.text == config.function.retType?.typeReference?.text

    override fun getNewName(): String = config.name
    override fun isNameChanged(): Boolean = config.nameChanged()

    override fun getMethod(): PsiElement = config.function
    override fun getLanguage(): Language = RsLanguage
}

/**
 * This type needs to be comparable by identity, not value.
 */
class Parameter(var pat: RsPat, private var typeReference: RsTypeReference? = null, val index: Int = NEW_PARAMETER) {
    val type: Ty
        get() = typeReference?.type ?: TyUnit
    val displayType: RsTypeReference
        get() = typeReference ?: RsPsiFactory(pat.project).createType("()")

    fun changeType(typeReference: RsTypeReference) {
        this.typeReference = typeReference
    }

    val patText: String
        get() = pat.text
}

/**
 * This class holds information about function's properties (name, return type, parameters, etc.).
 * It is designed to be changed (mutably) in the Change Signature dialog.
 *
 * After the dialog finishes, the refactoring will compare the state of the original function with the modified config
 * and perform the necessary adjustments.
 */
class RsChangeFunctionSignatureConfig private constructor(
    function: RsFunction,
    var name: String,
    parameters: List<Parameter>,
    var returnTypeDisplay: RsTypeReference?,
    var visibility: RsVis? = null,
    var isAsync: Boolean = false,
    var isUnsafe: Boolean = false
) : RsFunctionSignatureConfig(function) {
    override val parameterTypes: List<Ty>
        get() = parameters.map { it.type }

    override val returnType: Ty
        get() = returnTypeDisplay?.type ?: TyUnit

    val returnTypeReference: RsTypeReference
        get() = returnTypeDisplay ?: RsPsiFactory(function.project).createType("()")

    val allowsVisibilityChange: Boolean
        get() = !(function.owner is RsAbstractableOwner.Trait || function.owner.isTraitImpl)

    val parameters: MutableList<Parameter> = parameters.toMutableList()

    private val originalName: String = function.name.orEmpty()

    private val parametersText: String
        get() = parameters.joinToString(", ") { "${it.patText}: ${it.displayType.text}" }

    fun signature(): String = buildString {
        visibility?.let { append("${it.text} ") }

        if (isAsync) {
            append("async ")
        }
        if (isUnsafe) {
            append("unsafe ")
        }
        append("fn $name$typeParametersText($parametersText)")
        if (returnType !is TyUnit) {
            append(" -> ${returnTypeReference.text}")
        }
        append(whereClausesText)
    }

    fun createChangeInfo(): ChangeInfo = RsSignatureChangeInfo(this)
    fun nameChanged(): Boolean = name != originalName

    companion object {
        fun create(function: RsFunction): RsChangeFunctionSignatureConfig {
            val factory = RsPsiFactory(function.project)
            val parameters = function.valueParameters.mapIndexed { index, parameter ->
                Parameter(parameter.pat ?: factory.createPat("_"), parameter.typeReference, index)
            }
            return RsChangeFunctionSignatureConfig(
                function,
                function.name.orEmpty(),
                parameters,
                function.retType?.typeReference,
                function.vis,
                function.isAsync,
                function.isUnsafe
            )
        }
    }
}
