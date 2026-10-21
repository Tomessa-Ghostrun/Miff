package com.tomessa.miff.ui.diagnostic

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.data.AppCheckOutcome
import com.tomessa.miff.data.Test2Result
import com.tomessa.miff.domain.auth.CredentialsInputProblem
import com.tomessa.miff.domain.auth.SignInFailure
import com.tomessa.miff.domain.diagnostic.EXPECTED_FIREBASE_PROJECT_ID
import com.tomessa.miff.domain.diagnostic.OfferState
import com.tomessa.miff.domain.diagnostic.RulesProbeOutcome
import com.tomessa.miff.domain.diagnostic.isExpectedProject
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPlaceholder
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel,
    onOpenHome: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.diag_title), style = textDisplayTitle, color = colorTextPrimary)
        Text(stringResource(R.string.diag_subtitle), style = textBodyRegular14, color = colorTextDescription)
        TextButton(onClick = onOpenHome) {
            Text(stringResource(R.string.diag_open_home), style = textBodyBold14, color = colorActionPrimary)
        }

        BuildIdentityCard(projectId = state.firebaseProjectId)

        SectionCard(stringResource(R.string.diag_test1_title)) {
            Body(stringResource(R.string.diag_test1_description))
            MiffButton(
                text = stringResource(R.string.diag_test1_action),
                onClick = viewModel::runTest1,
                enabled = state.test1 !is Test1State.Running,
            )
            when (val t = state.test1) {
                Test1State.Idle -> Unit
                Test1State.Running -> Body(stringResource(R.string.diag_test1_running))
                is Test1State.Done -> Test1Result(t.outcome)
            }
        }

        SectionCard(stringResource(R.string.diag_test2_title)) {
            Body(stringResource(R.string.diag_test2_description))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.diag_test2_email_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors(),
                shape = ShapeMd,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.diag_test2_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors(),
                shape = ShapeMd,
                modifier = Modifier.fillMaxWidth(),
            )
            MiffButton(
                text = stringResource(R.string.diag_test2_action),
                onClick = {
                    viewModel.runTest2(email, password)
                    password = "" // never keep the password longer than the request
                },
                enabled = state.test2 !is Test2State.Running,
            )
            when (val t = state.test2) {
                Test2State.Idle -> Unit
                Test2State.Running -> Body(stringResource(R.string.diag_test2_running))
                is Test2State.InputProblem -> Warning(inputProblemText(t.problem))
                is Test2State.Done -> Test2ResultView(t.result)
            }
        }

        SectionCard(stringResource(R.string.diag_appcheck_title)) {
            Body(stringResource(R.string.diag_appcheck_description))
            MiffButton(
                text = stringResource(R.string.diag_appcheck_action),
                onClick = viewModel::checkAppCheck,
                enabled = state.appCheck !is AppCheckState.Running,
            )
            when (val a = state.appCheck) {
                AppCheckState.Idle -> {}

                AppCheckState.Running -> {
                    Body(stringResource(R.string.diag_appcheck_running))
                }

                is AppCheckState.Done -> {
                    when (a.outcome) {
                        AppCheckOutcome.TokenObtained -> Strong(stringResource(R.string.diag_appcheck_ok))
                        AppCheckOutcome.NoToken -> Body(stringResource(R.string.diag_appcheck_none))
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.diag_privacy_title)) {
            Body(stringResource(R.string.diag_privacy_description))
            SwitchRow(
                label = stringResource(R.string.diag_switch_analytics),
                checked = state.analyticsEnabled,
                onCheckedChange = viewModel::setAnalyticsEnabled,
            )
            SwitchRow(
                label = stringResource(R.string.diag_switch_crash),
                checked = state.crashReportsEnabled,
                onCheckedChange = viewModel::setCrashReportsEnabled,
            )
            MiffButton(
                text = stringResource(R.string.diag_event_action),
                onClick = viewModel::sendTestEvent,
            )
            MiffButton(
                text = stringResource(R.string.diag_event_location_action),
                onClick = viewModel::tryToSendPosition,
            )
            state.eventFeedback?.let { EventFeedbackView(it) }
            MiffButton(
                text = stringResource(R.string.diag_crash_action),
                onClick = viewModel::triggerTestCrash,
                enabled = state.crashReportsEnabled,
            )
            if (!state.crashReportsEnabled) Body(stringResource(R.string.diag_crash_hint))
        }

        TokenGallery()

        LanguageCard()
    }
}

@Composable
private fun BuildIdentityCard(projectId: String?) {
    SectionCard(stringResource(R.string.diag_build_title)) {
        Body(stringResource(R.string.diag_build_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Body(stringResource(R.string.diag_build_type, BuildConfig.BUILD_TYPE))
        Body(stringResource(R.string.diag_build_app_id, BuildConfig.APPLICATION_ID))
        Body(stringResource(R.string.diag_build_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        Body(stringResource(R.string.diag_build_project, projectId ?: "?"))
        when {
            projectId == null -> Warning(stringResource(R.string.diag_project_unknown))
            isExpectedProject(projectId) -> Strong(stringResource(R.string.diag_project_ok))
            else -> Warning(stringResource(R.string.diag_project_warning, EXPECTED_FIREBASE_PROJECT_ID))
        }
        val configuration = LocalConfiguration.current
        Body(stringResource(R.string.diag_build_language, configuration.locales[0].toLanguageTag()))
        val direction =
            if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                R.string.diag_direction_rtl
            } else {
                R.string.diag_direction_ltr
            }
        Body(stringResource(R.string.diag_build_direction, stringResource(direction)))
    }
}

@Composable
private fun Test1Result(outcome: RulesProbeOutcome) {
    when (outcome) {
        RulesProbeOutcome.RULES_ACTIVE -> {
            Strong(stringResource(R.string.diag_test1_ok))
            Body(stringResource(R.string.diag_test1_ok_detail))
        }

        RulesProbeOutcome.RULES_OPEN -> {
            Warning(stringResource(R.string.diag_test1_open))
        }

        RulesProbeOutcome.OFFLINE -> {
            Body(stringResource(R.string.diag_test1_offline))
        }

        RulesProbeOutcome.INCONCLUSIVE -> {
            Body(stringResource(R.string.diag_test1_inconclusive))
        }
    }
}

@Composable
private fun Test2ResultView(result: Test2Result) {
    when (result) {
        is Test2Result.Success -> {
            Strong(stringResource(R.string.diag_test2_success))
            Body(
                stringResource(
                    if (result.profileExists) {
                        R.string.diag_test2_profile_found
                    } else {
                        R.string.diag_test2_profile_missing
                    },
                ),
            )
            when (val offer = result.offer) {
                OfferState.Classic -> {
                    Body(stringResource(R.string.diag_test2_offer_classic))
                }

                is OfferState.Premium -> {
                    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, LocalLocale.current.platformLocale)
                    val date = formatter.format(Date(offer.expiresAtMillis))
                    Body(stringResource(R.string.diag_test2_offer_premium, date))
                }
            }
        }

        is Test2Result.SignInFailed -> {
            Body(stringResource(signInFailureText(result.failure)))
        }

        Test2Result.ReadDenied -> {
            Warning(stringResource(R.string.diag_test2_read_denied))
        }

        Test2Result.ReadFailed -> {
            Body(stringResource(R.string.diag_test2_read_failed))
        }
    }
}

private fun signInFailureText(failure: SignInFailure): Int =
    when (failure) {
        SignInFailure.INVALID_CREDENTIALS -> R.string.diag_signin_invalid
        SignInFailure.NETWORK -> R.string.diag_signin_network
        SignInFailure.TOO_MANY_ATTEMPTS -> R.string.diag_signin_too_many
        SignInFailure.ACCOUNT_DISABLED -> R.string.diag_signin_disabled
        SignInFailure.UNKNOWN -> R.string.diag_signin_unknown
    }

@Composable
private fun inputProblemText(problem: CredentialsInputProblem): String =
    stringResource(
        when (problem) {
            CredentialsInputProblem.EMAIL_EMPTY -> R.string.diag_input_email_empty
            CredentialsInputProblem.EMAIL_INVALID -> R.string.diag_input_email_invalid
            CredentialsInputProblem.PASSWORD_EMPTY -> R.string.diag_input_password_empty
            CredentialsInputProblem.TOO_LONG -> R.string.diag_input_too_long
        },
    )

@Composable
private fun EventFeedbackView(feedback: EventFeedback) {
    when (feedback) {
        EventFeedback.Sent -> {
            Strong(stringResource(R.string.diag_event_sent))
        }

        EventFeedback.Disabled -> {
            Body(stringResource(R.string.diag_event_disabled))
        }

        is EventFeedback.Refused -> {
            Body(stringResource(R.string.diag_event_refused, feedback.reason.name.lowercase(Locale.ROOT)))
        }

        EventFeedback.LocationRefused -> {
            Strong(stringResource(R.string.diag_event_location_refused))
        }

        EventFeedback.LocationNotRefused -> {
            Warning(stringResource(R.string.diag_event_location_unexpected))
        }
    }
}

@Composable
private fun LanguageCard() {
    SectionCard(stringResource(R.string.diag_lang_title)) {
        Body(stringResource(R.string.diag_lang_long))
        Body(stringResource(R.string.diag_lang_test_text))
        Body(pluralStringResource(R.plurals.diag_lang_members, PLURAL_SAMPLE, PLURAL_SAMPLE))
    }
}

private const val PLURAL_SAMPLE = 3

// ---- Small building blocks (diagnostic screen only) ----

@Composable
internal fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = ShapeMd,
        colors = CardDefaults.cardColors(containerColor = colorBgSurface),
        border = BorderStroke(StrokeHairline, colorBorderDefault),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(text = title, style = textBodyBold16, color = colorTextPrimary)
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = textBodyRegular14, color = colorTextPrimary)
}

@Composable
private fun Strong(text: String) {
    Text(text = text, style = textBodyBold14, color = colorTextPrimary)
}

@Composable
private fun Warning(text: String) {
    Text(text = text, style = textBodyBold14, color = colorTextError)
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(text = label, style = textBodyRegular, color = colorTextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colorTextOnAction,
                    checkedTrackColor = colorActionPrimary,
                    uncheckedThumbColor = colorTextOnAction,
                    uncheckedTrackColor = colorBorderStrong,
                    uncheckedBorderColor = colorBorderStrong,
                ),
        )
    }
}

@Composable
private fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colorBgSurface,
        unfocusedContainerColor = colorBgSurface,
        focusedTextColor = colorTextForm,
        unfocusedTextColor = colorTextForm,
        focusedBorderColor = colorActionPrimary,
        unfocusedBorderColor = colorBorderStrong,
        focusedLabelColor = colorActionPrimary,
        unfocusedLabelColor = colorTextPlaceholder,
        cursorColor = colorActionPrimary,
    )
