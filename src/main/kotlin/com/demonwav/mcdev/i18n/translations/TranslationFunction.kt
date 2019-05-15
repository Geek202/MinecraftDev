/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2018 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.i18n.translations

import com.demonwav.mcdev.facet.MinecraftFacet
import com.demonwav.mcdev.i18n.reference.I18nReference
import com.demonwav.mcdev.platform.mcp.McpModuleType
import com.demonwav.mcdev.platform.mcp.srg.SrgManager
import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.evaluate
import com.demonwav.mcdev.util.extractVarArgs
import com.demonwav.mcdev.util.findModule
import com.demonwav.mcdev.util.isSameReference
import com.demonwav.mcdev.util.referencedMethod
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod

class TranslationFunction(
    private val memberReference: MemberReference,
    private val matchedIndex: Int, val formatting: Boolean, val setter: Boolean = false,
    val foldParameters: Boolean = false, val prefix: String = "", val suffix: String = "",
    val obfuscatedName: Boolean = false
) {
    private fun getMethod(context: PsiElement): PsiMethod? {
        var reference = memberReference
        if (obfuscatedName) {
            val moduleSrgManager = context.findModule()?.let { MinecraftFacet.getInstance(it, McpModuleType)?.srgManager }
            val srgManager = moduleSrgManager ?: SrgManager.findAnyInstance(context.project)
            srgManager?.srgMapNow?.mapToMcpMethod(memberReference)?.let {
                reference = it
            }
        }
        return reference.resolveMember(context.project) as? PsiMethod
    }

    fun matches(call: PsiCall, paramIndex: Int): Boolean {
        val referenceMethod = getMethod(call) ?: return false
        val method = call.resolveMethod() ?: return false
        return method.isSameReference(referenceMethod) && paramIndex == matchedIndex
    }

    fun getTranslationKey(call: PsiCall): Translation.Key? {
        if (!matches(call, matchedIndex)) {
            return null
        }
        val param = call.argumentList?.expressions?.get(matchedIndex) ?: return null
        val value = ((param as? PsiLiteral)?.value as? String) ?: return null
        return Translation.Key(prefix, value, suffix)
    }

    fun format(translation: String, call: PsiCall): Pair<String, Int>? {
        if (!matches(call, matchedIndex)) {
            return null
        }
        if (!formatting) {
            return translation to -1
        }

        val format = NUMBER_FORMATTING_PATTERN.replace(translation, "%$1s")
        val paramCount = STRING_FORMATTING_PATTERN.findAll(format).count()

        val method = call.referencedMethod ?: return translation to -1
        val varargs = call.extractVarArgs(method.parameterList.parametersCount - 1, true, true)
        val varargStart = if (varargs.size > paramCount) method.parameterList.parametersCount - 1 + paramCount else -1
        return String.format(format, *varargs) to varargStart
    }

    override fun toString(): String {
        return "$memberReference@$matchedIndex"
    }

    companion object {
        val NUMBER_FORMATTING_PATTERN = Regex("%(\\d+\\$)?[\\d.]*[df]")
        val STRING_FORMATTING_PATTERN = Regex("[^%]?%(?:\\d+\\$)?s")
    }
}
