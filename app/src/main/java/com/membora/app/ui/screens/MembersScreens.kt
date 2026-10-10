package com.membora.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.membora.app.data.MemboraRepository
import com.membora.app.data.PreparedUpload
import com.membora.app.data.UploadCompression
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import com.membora.app.domain.*
import com.membora.app.ui.components.*
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MembersScreen(repository: MemboraRepository, onOpenMember: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Active", "Expiring", "Expired", "Due")
    val visible = repository.members.filter { member ->
        val matchesSearch = search.isBlank() || listOf(member.fullName, member.code, member.mobile, member.email).any { it.contains(search, true) }
        val status = repository.memberStatus(member.id)
        val membership = repository.membershipFor(member.id)
        val matchesFilter = when (filter) {
            "Active" -> status == MemberStatus.ACTIVE
            "Expiring" -> status == MemberStatus.EXPIRING_SOON
            "Expired" -> status == MemberStatus.EXPIRED
            "Due" -> membership?.paymentStatus in listOf(PaymentStatus.DUE, PaymentStatus.PARTIAL)
            else -> true
        }
        matchesSearch && matchesFilter
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            EnterprisePageHeader("Members", "${repository.members.size} people across ${repository.organization.branchName}")
            Spacer(Modifier.height(18.dp))
            SearchBox(search, { search = it }, "Search name, phone, email or ID")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) }) }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyState("No members found", "Try a different search or filter.") }
        } else {
            items(visible, key = { it.id }) { member ->
                val membership = repository.membershipFor(member.id)
                val plan = membership?.let { repository.plan(it.planId) }
                AppCard(onClick = { onOpenMember(member.id) }) {
                    MemberRow(member, repository.memberStatus(member.id), "${member.code} • ${plan?.name ?: "No membership"}", onClick = { onOpenMember(member.id) })
                    if (membership != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(Modifier.fillMaxWidth()) {
                            Text("Ends ${membership.endDate.toUiDate()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(membership.paymentStatus.pretty(), style = MaterialTheme.typography.labelMedium, color = if (membership.paymentStatus == PaymentStatus.PAID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun AddMemberScreen(repository: MemboraRepository, onBack: () -> Unit, onCreated: (String) -> Unit) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var alternatePhone by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var dateOfBirth by rememberSaveable { mutableStateOf("") }
    var addressLine by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var postalCode by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var emergencyName by rememberSaveable { mutableStateOf("") }
    var emergencyPhone by rememberSaveable { mutableStateOf("") }
    var emergencyRelationship by rememberSaveable { mutableStateOf("") }
    val customFields = remember { mutableStateListOf<CustomFieldDraft>() }
    var selectedPlan by rememberSaveable { mutableStateOf<String?>(repository.plans.firstOrNull()?.id) }
    var membershipStartDate by rememberSaveable { mutableStateOf(repository.currentDate().toString()) }
    var membershipEndDate by rememberSaveable {
        mutableStateOf(repository.plans.firstOrNull()?.let { repository.calculateMembershipEndDate(it.id, repository.currentDate()) }?.toString().orEmpty())
    }
    var payment by rememberSaveable { mutableStateOf(repository.plans.firstOrNull()?.let { (it.price + it.registrationFee).asMoney() } ?: "") }
    var paymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.UPI) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<PreparedUpload?>(null) }
    var preparingPhoto by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var photoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showPhotoSourcePicker by rememberSaveable { mutableStateOf(false) }
    var cameraPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var createdMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var welcomeDraft by remember { mutableStateOf<MemberMessageDraft?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prepareSelectedPhoto: (Uri) -> Unit = { uri ->
        preparingPhoto = true
        photoMessage = "Preparing cropped profile photo…"
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                UploadCompression.preparePhoto(context.contentResolver, uri)
            }
            prepared.fold(
                onSuccess = {
                    pendingPhoto = it
                    preparingPhoto = false
                    photoMessage = "Photo ready (${it.bytes.size.toReadableSize()})${it.savingsLabel()}"
                },
                onFailure = {
                    pendingPhoto = null
                    preparingPhoto = false
                    photoMessage = it.localizedMessage ?: "Unable to prepare that photo"
                }
            )
        }
    }
    val cropProfilePhoto = rememberSquareImageCropper(
        title = "Crop profile photo",
        shape = ImageCropShape.CIRCLE,
        onCropped = prepareSelectedPhoto,
        onError = { photoMessage = it }
    )
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(cropProfilePhoto)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) cameraPhotoUri?.let(cropProfilePhoto)
        else cameraPhotoUri = null
    }
    val parsedBirthDate = parseOptionalDate(dateOfBirth)
    val emailValid = email.isBlank() || email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val birthDateValid = dateOfBirth.isBlank() || (parsedBirthDate != null && !parsedBirthDate.isAfter(repository.currentDate()))
    val emergencyPhoneValid = emergencyPhone.isBlank() || emergencyPhone.count(Char::isDigit) >= 8
    val customFieldsValid = customFields.all { it.key.isBlank() == it.value.isBlank() } &&
        customFields.map { it.key.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank).let { it.size == it.distinct().size }
    val parsedMembershipStart = parseOptionalDate(membershipStartDate)
    val parsedMembershipEnd = parseOptionalDate(membershipEndDate)
    val membershipDatesValid = selectedPlan == null || (parsedMembershipStart != null && parsedMembershipEnd != null && !parsedMembershipEnd.isBefore(parsedMembershipStart))
    val selectedPlanValue = selectedPlan?.let(repository::plan)?.let { it.price + it.registrationFee } ?: 0.0
    val paymentValue = payment.replace(",", "").toDoubleOrNull() ?: 0.0
    val paymentValid = selectedPlan == null || paymentValue in 0.0..selectedPlanValue
    val valid = firstName.isNotBlank() && mobile.count(Char::isDigit) >= 8 && emailValid &&
        birthDateValid && emergencyPhoneValid && customFieldsValid && membershipDatesValid && paymentValid

    fun offerWelcomeMessage(memberId: String) {
        createdMemberId = memberId
        welcomeDraft = runCatching { repository.welcomeMessageDraft(memberId) }.getOrNull()
        if (welcomeDraft == null) onCreated(memberId)
    }

    welcomeDraft?.let { draft ->
        MemberMessageDialog(repository, draft) {
            val memberId = createdMemberId
            welcomeDraft = null
            createdMemberId = null
            if (memberId != null) onCreated(memberId)
        }
    }

    Scaffold(
        topBar = { DetailTopBar("Add member", "Registration", onBack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            if (saving || preparingPhoto) return@Button
                            submitted = true
                            if (valid) {
                                saving = true
                                val id = runCatching { repository.addMember(
                                    NewMemberRequest(
                                        firstName = firstName,
                                        lastName = lastName,
                                        mobile = mobile,
                                        email = email,
                                        city = city,
                                        emergencyContact = "",
                                        planId = selectedPlan,
                                        paymentAmount = payment.replace(",", "").toDoubleOrNull() ?: 0.0,
                                        paymentMethod = paymentMethod,
                                        gender = gender,
                                        dateOfBirth = parsedBirthDate,
                                        alternatePhone = alternatePhone,
                                        addressLine = addressLine,
                                        postalCode = postalCode,
                                        notes = notes,
                                        emergencyContactName = emergencyName,
                                        emergencyContactPhone = emergencyPhone,
                                        emergencyContactRelationship = emergencyRelationship,
                                        customValues = customFields.toCustomValues(),
                                        membershipStartDate = parsedMembershipStart,
                                        membershipEndDate = parsedMembershipEnd
                                    )
                                ) }.getOrElse {
                                    saving = false
                                    photoMessage = it.localizedMessage ?: "Unable to create member"
                                    return@Button
                                }
                                val photo = pendingPhoto
                                if (photo == null) {
                                    saving = false
                                    offerWelcomeMessage(id)
                                } else {
                                    repository.uploadMemberPhoto(
                                        memberId = id,
                                        bytes = photo.bytes,
                                        contentType = photo.contentType
                                    ) { result ->
                                        saving = false
                                        if (result.isFailure) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Member created. Photo upload failed; retry from the member profile.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    offerWelcomeMessage(id)
                                }
                            }
                        },
                        enabled = !saving && !preparingPhoto,
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp).height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            when {
                                saving -> "Creating member…"
                                selectedPlan == null -> "Create member"
                                else -> "Create member & activate"
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                FormSectionTitle("01", "Personal details", "The essentials staff use every day")
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 520.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(firstName, { firstName = it }, Modifier.fillMaxWidth(), label = { Text("First name *") }, singleLine = true, isError = submitted && firstName.isBlank())
                            OutlinedTextField(lastName, { lastName = it }, Modifier.fillMaxWidth(), label = { Text("Last name") }, singleLine = true)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(firstName, { firstName = it }, Modifier.weight(1f), label = { Text("First name *") }, singleLine = true, isError = submitted && firstName.isBlank())
                            OutlinedTextField(lastName, { lastName = it }, Modifier.weight(1f), label = { Text("Last name") }, singleLine = true)
                        }
                    }
                }
                Text("Gender", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Female", "Male", "Non-binary", "Prefer not to say").forEach { option ->
                        FilterChip(selected = gender == option, onClick = { gender = if (gender == option) "" else option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(
                    dateOfBirth, { dateOfBirth = it }, Modifier.fillMaxWidth(),
                    label = { Text("Date of birth") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true,
                    isError = submitted && !birthDateValid,
                    supportingText = if (submitted && !birthDateValid) ({ Text("Use YYYY-MM-DD and do not enter a future date") }) else null
                )
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
                OutlinedButton(
                    onClick = { showPhotoSourcePicker = true },
                    enabled = !preparingPhoto && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pendingPhoto == null) "Select profile photo" else "Change profile photo")
                }
                photoMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                FormSectionTitle("02", "Contact & address", "Reach the member and keep accurate records")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text("Mobile number *") }, singleLine = true, isError = submitted && mobile.count(Char::isDigit) < 8, supportingText = if (submitted && mobile.count(Char::isDigit) < 8) ({ Text("Enter at least 8 digits") }) else null)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, isError = submitted && !emailValid, supportingText = if (submitted && !emailValid) ({ Text("Enter a valid email address") }) else null)
                OutlinedTextField(alternatePhone, { alternatePhone = it }, Modifier.fillMaxWidth(), label = { Text("Alternate phone") }, singleLine = true)
                OutlinedTextField(addressLine, { addressLine = it }, Modifier.fillMaxWidth(), label = { Text("Street address") })
                OutlinedTextField(city, { city = it }, Modifier.fillMaxWidth(), label = { Text("City") }, singleLine = true)
                OutlinedTextField(postalCode, { postalCode = it }, Modifier.fillMaxWidth(), label = { Text("Postal code") }, singleLine = true)
            }
            item {
                FormSectionTitle("03", "Emergency contact", "Used only when the member needs assistance")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(emergencyName, { emergencyName = it }, Modifier.fillMaxWidth(), label = { Text("Contact name") }, singleLine = true)
                OutlinedTextField(emergencyRelationship, { emergencyRelationship = it }, Modifier.fillMaxWidth(), label = { Text("Relationship") }, singleLine = true)
                OutlinedTextField(emergencyPhone, { emergencyPhone = it }, Modifier.fillMaxWidth(), label = { Text("Emergency phone") }, singleLine = true, isError = submitted && !emergencyPhoneValid, supportingText = if (submitted && !emergencyPhoneValid) ({ Text("Enter at least 8 digits") }) else null)
            }
            item {
                FormSectionTitle("04", "Custom fields", "Capture organization-specific member information")
                Spacer(Modifier.height(10.dp))
                CustomFieldsEditor(customFields, showErrors = submitted)
            }
            item {
                FormSectionTitle("05", "Select membership", "Plans remain configurable for every organization")
            }
            items(repository.plans.filter { it.active }) { plan ->
                val selected = selectedPlan == plan.id
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedPlan = plan.id
                        payment = (plan.price + plan.registrationFee).asMoney()
                        parseOptionalDate(membershipStartDate)?.let { start ->
                            membershipEndDate = repository.calculateMembershipEndDate(plan.id, start)?.toString().orEmpty()
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium)
                            Text("${plan.durationValue} ${plan.durationUnit.lowercase()} • ${plan.serviceIds.size} services", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${repository.organization.currencySymbol}${plan.price.asMoney()}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item {
                TextButton(onClick = { selectedPlan = null; payment = "" }) { Text("Register without a membership") }
                if (selectedPlan != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Membership term", style = MaterialTheme.typography.titleMedium)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val startField: @Composable (Modifier) -> Unit = { fieldModifier ->
                            OutlinedTextField(
                                membershipStartDate,
                                {
                                    membershipStartDate = it
                                    parseOptionalDate(it)?.let { start ->
                                        selectedPlan?.let { planId -> membershipEndDate = repository.calculateMembershipEndDate(planId, start)?.toString().orEmpty() }
                                    }
                                },
                                fieldModifier,
                                label = { Text("Start date *") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true,
                                isError = submitted && !membershipDatesValid
                            )
                        }
                        val endField: @Composable (Modifier) -> Unit = { fieldModifier ->
                            OutlinedTextField(
                                membershipEndDate, { membershipEndDate = it }, fieldModifier,
                                label = { Text("Expiry date *") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true,
                                isError = submitted && !membershipDatesValid
                            )
                        }
                        if (maxWidth < 500.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            startField(Modifier.fillMaxWidth()); endField(Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            startField(Modifier.weight(1f)); endField(Modifier.weight(1f))
                        }
                    }
                    if (submitted && !membershipDatesValid) Text("Enter valid dates; expiry cannot be before the start date", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    FormSectionTitle("06", "Payment", "Full or partial payments are supported")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(payment, { payment = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, Modifier.fillMaxWidth(), label = { Text("Amount received") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true, isError = submitted && !paymentValid, supportingText = if (submitted && !paymentValid) ({ Text("Payment cannot exceed ${repository.organization.currencySymbol}${selectedPlanValue.asMoney()}") }) else null)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.BANK_TRANSFER).forEach { method ->
                            FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { Text(method.pretty()) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
    if (showPhotoSourcePicker) {
        ProfilePhotoSourceDialog(
            onDismiss = { showPhotoSourcePicker = false },
            onCamera = {
                showPhotoSourcePicker = false
                runCatching {
                    createCameraPhotoUri(context).also { uri ->
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    }
                }.onFailure { photoMessage = it.localizedMessage ?: "Unable to open the camera" }
            },
            onGallery = {
                showPhotoSourcePicker = false
                photoPicker.launch("image/*")
            }
        )
    }
}

@Composable
fun MemberDetailScreen(repository: MemboraRepository, memberId: String, onBack: () -> Unit) {
    val member = repository.member(memberId)
    if (member == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Member not found")
            TextButton(onClick = onBack) { Text("Go back") }
        }
        return
    }
    val membership = repository.membershipFor(memberId)
    val plan = membership?.let { repository.plan(it.planId) }
    val outstandingDue = membership?.let { repository.outstandingBalance(it.id) } ?: 0.0
    var showPayment by rememberSaveable { mutableStateOf(false) }
    var showRenew by rememberSaveable { mutableStateOf(false) }
    var showFreeze by rememberSaveable { mutableStateOf(false) }
    var showCancelMembership by rememberSaveable { mutableStateOf(false) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadInProgress by rememberSaveable { mutableStateOf(false) }
    var showDocumentTypePicker by rememberSaveable { mutableStateOf(false) }
    var pendingDocumentType by rememberSaveable { mutableStateOf(MemberDocumentType.OTHER.name) }
    var showPhotoSourcePicker by rememberSaveable { mutableStateOf(false) }
    var showProfilePhoto by rememberSaveable { mutableStateOf(false) }
    var previewDocumentImage by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var pendingMessageDraft by remember { mutableStateOf<MemberMessageDraft?>(null) }
    var cameraPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val uploadSelectedPhoto: (Uri) -> Unit = { uri ->
        uploadInProgress = true
        message = "Preparing cropped photo…"
        scope.launch {
            val preparedResult = withContext(Dispatchers.IO) {
                UploadCompression.preparePhoto(context.contentResolver, uri)
            }
            preparedResult.fold(
                onSuccess = { prepared ->
                    message = "Uploading compressed photo (${prepared.bytes.size.toReadableSize()})…"
                    repository.uploadMemberPhoto(
                        memberId = memberId,
                        bytes = prepared.bytes,
                        contentType = prepared.contentType
                    ) { result ->
                        uploadInProgress = false
                        message = result.fold(
                            { "Profile photo updated${prepared.savingsLabel()}" },
                            { it.localizedMessage ?: "Photo upload failed" }
                        )
                    }
                },
                onFailure = {
                    uploadInProgress = false
                    message = it.localizedMessage ?: "Unable to prepare that photo"
                }
            )
        }
    }
    val cropProfilePhoto = rememberSquareImageCropper(
        title = "Crop profile photo",
        shape = ImageCropShape.CIRCLE,
        onCropped = uploadSelectedPhoto,
        onError = { message = it }
    )
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(cropProfilePhoto)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) cameraPhotoUri?.let(cropProfilePhoto)
        else cameraPhotoUri = null
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadInProgress = true
        message = "Optimizing document…"
        scope.launch {
            val preparedResult = withContext(Dispatchers.IO) {
                UploadCompression.prepareDocument(context.contentResolver, uri)
            }
            preparedResult.fold(
                onSuccess = { prepared ->
                    message = "Uploading document securely (${prepared.bytes.size.toReadableSize()})…"
                    repository.uploadMemberDocument(
                        memberId = memberId,
                        documentType = runCatching { MemberDocumentType.valueOf(pendingDocumentType) }
                            .getOrDefault(MemberDocumentType.OTHER),
                        fileName = prepared.fileName,
                        bytes = prepared.bytes,
                        contentType = prepared.contentType
                    ) { result ->
                        uploadInProgress = false
                        message = result.fold(
                            { "Document updated${prepared.savingsLabel()}; previous file removed" },
                            { it.localizedMessage ?: "Document upload failed" }
                        )
                    }
                },
                onFailure = {
                    uploadInProgress = false
                    message = it.localizedMessage ?: "Unable to prepare that document"
                }
            )
        }
    }

    if (showPayment) PaymentDialog(repository, memberId, membership?.id, onDismiss = { showPayment = false }, onSaved = { paymentId ->
        showPayment = false
        message = "Payment recorded and receipt created"
        pendingMessageDraft = runCatching { repository.paymentConfirmationDraft(paymentId) }.getOrNull()
    })
    if (showRenew) RenewDialog(repository, memberId, onDismiss = { showRenew = false }, onSaved = { renewalId ->
        showRenew = false
        message = "Renewal created successfully"
        pendingMessageDraft = runCatching { repository.renewalThankYouDraft(memberId, renewalId) }.getOrNull()
    })
    pendingMessageDraft?.let { draft ->
        MemberMessageDialog(repository, draft) { pendingMessageDraft = null }
    }
    if (showFreeze && membership != null) FreezeMembershipDialog(
        repository = repository,
        membership = membership,
        onDismiss = { showFreeze = false },
        onSaved = { showFreeze = false; message = "Membership frozen" }
    )
    if (showCancelMembership && membership != null) CancelMembershipDialog(
        repository = repository,
        membership = membership,
        onDismiss = { showCancelMembership = false },
        onSaved = { showCancelMembership = false; message = "Membership cancelled" }
    )
    if (showEditProfile) EditMemberDialog(
        repository = repository,
        member = member,
        onDismiss = { showEditProfile = false },
        onSaved = { showEditProfile = false; message = "Member profile updated" }
    )
    if (showDocumentTypePicker) {
        AlertDialog(
            onDismissRequest = { showDocumentTypePicker = false },
            title = { Text("Document type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Uploading the same type replaces and permanently removes its previous file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    MemberDocumentType.values().forEach { type ->
                        TextButton(
                            onClick = {
                                pendingDocumentType = type.name
                                showDocumentTypePicker = false
                                documentPicker.launch(MEMBER_DOCUMENT_MIME_TYPES)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(type.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDocumentTypePicker = false }) { Text("Cancel") }
            }
        )
    }
    if (showPhotoSourcePicker) {
        ProfilePhotoSourceDialog(
            onDismiss = { showPhotoSourcePicker = false },
            onCamera = {
                showPhotoSourcePicker = false
                runCatching {
                    createCameraPhotoUri(context).also { uri ->
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    }
                }.onFailure { message = it.localizedMessage ?: "Unable to open the camera" }
            },
            onGallery = {
                showPhotoSourcePicker = false
                photoPicker.launch("image/*")
            }
        )
    }
    if (showProfilePhoto) {
        ProfilePhotoDialog(member = member, onDismiss = { showProfilePhoto = false })
    }
    previewDocumentImage?.let { (uri, title) ->
        ZoomableImageDialog(
            title = title,
            subtitle = "Image document",
            imageModel = uri,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onDismiss = { previewDocumentImage = null }
        )
    }

    Scaffold(topBar = { DetailTopBar(member.fullName, member.code, onBack) }) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(
                        member = member,
                        large = true,
                        onPhotoClick = { showProfilePhoto = true }
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.fullName, style = MaterialTheme.typography.headlineMedium)
                        Text("${member.mobile} • ${member.city.ifBlank { "No city" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(7.dp))
                        StatusPill(repository.memberStatus(member.id))
                    }
                }
            }
            message?.let { notice ->
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notice, Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            TextButton(onClick = { message = null }) { Text("OK") }
                        }
                    }
                }
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 480.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showEditProfile = true }, Modifier.fillMaxWidth()) { Text("Edit profile") }
                            Button(onClick = { showPayment = true }, enabled = membership != null && membership.status != MembershipStatus.CANCELLED && membership.paymentStatus != PaymentStatus.PAID, modifier = Modifier.fillMaxWidth()) { Text("Add payment") }
                            OutlinedButton(onClick = { showRenew = true }, Modifier.fillMaxWidth()) { Text("Renew") }
                            OutlinedButton(onClick = {
                                message = repository.checkIn(memberId).fold({ "Check-in successful" }, { it.message ?: "Unable to check in" })
                            }, Modifier.fillMaxWidth()) { Text("Check in") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showEditProfile = true }, Modifier.weight(1f)) { Text("Edit profile") }
                            Button(onClick = { showPayment = true }, enabled = membership != null && membership.status != MembershipStatus.CANCELLED && membership.paymentStatus != PaymentStatus.PAID, modifier = Modifier.weight(1f)) { Text("Add payment") }
                            OutlinedButton(onClick = { showRenew = true }, Modifier.weight(1f)) { Text("Renew") }
                            OutlinedButton(onClick = {
                                message = repository.checkIn(memberId).fold({ "Check-in successful" }, { it.message ?: "Unable to check in" })
                            }, Modifier.weight(1f)) { Text("Check in") }
                        }
                    }
                }
                if (outstandingDue > 0.009) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            pendingMessageDraft = runCatching { repository.dueReminderDraft(memberId) }
                                .onFailure { message = it.localizedMessage ?: "Unable to create reminder" }
                                .getOrNull()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send due reminder • ${repository.organization.currencySymbol}${outstandingDue.asMoney()}")
                    }
                }
            }
            item {
                SectionHeading("Membership")
                if (membership == null) {
                    AppCard { Text("No membership assigned") }
                } else {
                    val days = ChronoUnit.DAYS.between(repository.currentDate(), membership.endDate).coerceAtLeast(0)
                    AppCard {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(plan?.name ?: "Membership", style = MaterialTheme.typography.titleLarge)
                                Text("${membership.startDate.toUiDate()} — ${membership.endDate.toUiDate()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(repository.memberStatus(member.id))
                        }
                        Spacer(Modifier.height(20.dp))
                        Row {
                            DetailStat("Days left", days.toString(), Modifier.weight(1f))
                            DetailStat("Plan value", "${repository.organization.currencySymbol}${membership.finalAmount.asMoney()}", Modifier.weight(1f))
                            DetailStat("Payment", membership.paymentStatus.pretty(), Modifier.weight(1f))
                        }
                        if (membership.paymentStatus != PaymentStatus.PAID) {
                            Spacer(Modifier.height(16.dp))
                            val paid = repository.paymentsFor(memberId).filter { it.membershipId == membership.id }.sumOf { it.amount }
                            val due = (membership.finalAmount - paid).coerceAtLeast(0.0)
                            LinearProgressIndicator(progress = { (paid / membership.finalAmount).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text("${repository.organization.currencySymbol}${due.asMoney()} outstanding", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                        if (membership.status == MembershipStatus.FROZEN || membership.status == MembershipStatus.CANCELLED) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                when (membership.status) {
                                    MembershipStatus.FROZEN -> listOfNotNull("Frozen", membership.freezeUntil?.let { "until ${it.toUiDate()}" }, membership.statusReason.takeIf(String::isNotBlank)).joinToString(" • ")
                                    MembershipStatus.CANCELLED -> listOfNotNull("Cancelled", membership.cancelledOn?.let { "on ${it.toUiDate()}" }, membership.statusReason.takeIf(String::isNotBlank)).joinToString(" • ")
                                    else -> ""
                                },
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (membership.status != MembershipStatus.CANCELLED) {
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (membership.status == MembershipStatus.FROZEN) {
                                    OutlinedButton(
                                        onClick = {
                                            message = repository.resumeMembership(membership.id).fold(
                                                { "Membership resumed; expiry adjusted" },
                                                { it.localizedMessage ?: "Unable to resume membership" }
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Resume") }
                                } else {
                                    OutlinedButton(onClick = { showFreeze = true }, modifier = Modifier.weight(1f)) { Text("Freeze") }
                                }
                                TextButton(onClick = { showCancelMembership = true }, modifier = Modifier.weight(1f)) { Text("Cancel membership", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            val membershipHistory = repository.membershipsFor(memberId)
            if (membershipHistory.size > 1) {
                item {
                    SectionHeading("Membership history")
                    AppCard {
                        membershipHistory.forEachIndexed { index, item ->
                            val itemPlan = repository.plan(item.planId)
                            InfoRow(
                                itemPlan?.name ?: "Membership",
                                "${item.startDate.toUiDate()} – ${item.endDate.toUiDate()} • ${repository.effectiveStatus(item).pretty()}",
                                last = index == membershipHistory.lastIndex
                            )
                        }
                    }
                }
            }
            item {
                SectionHeading("Contact & profile")
                AppCard {
                    InfoRow("Mobile", member.mobile)
                    InfoRow("Email", member.email.ifBlank { "Not provided" })
                    InfoRow("Alternate phone", member.alternatePhone.ifBlank { "Not provided" })
                    InfoRow("Gender", member.gender.ifBlank { "Not provided" })
                    InfoRow("Date of birth", member.dateOfBirth?.toUiDate() ?: "Not provided")
                    InfoRow("Address", listOf(member.addressLine, member.city, member.postalCode).filter(String::isNotBlank).joinToString(", ").ifBlank { "Not provided" })
                    InfoRow("Notes", member.notes.ifBlank { "Not provided" }, last = true)
                }
            }
            item {
                SectionHeading("Emergency contact")
                AppCard {
                    val legacyEmergency = member.emergencyContact.ifBlank { "Not provided" }
                    InfoRow("Name", member.emergencyContactName.ifBlank { legacyEmergency })
                    InfoRow("Relationship", member.emergencyContactRelationship.ifBlank { "Not provided" })
                    InfoRow("Phone", member.emergencyContactPhone.ifBlank { "Not provided" }, last = true)
                }
            }
            if (member.customValues.isNotEmpty()) {
                item {
                    SectionHeading("Custom fields")
                    AppCard {
                        member.customValues.entries.forEachIndexed { index, entry ->
                            InfoRow(entry.key, entry.value, last = index == member.customValues.size - 1)
                        }
                    }
                }
            }
            item {
                SectionHeading("Photos & documents")
                AppCard {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 430.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showPhotoSourcePicker = true }, enabled = !uploadInProgress, modifier = Modifier.fillMaxWidth()) { Text("Upload profile photo") }
                                OutlinedButton(onClick = { showDocumentTypePicker = true }, enabled = !uploadInProgress, modifier = Modifier.fillMaxWidth()) { Text("Add document") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showPhotoSourcePicker = true }, enabled = !uploadInProgress, modifier = Modifier.weight(1f)) { Text("Upload profile photo") }
                                OutlinedButton(onClick = { showDocumentTypePicker = true }, enabled = !uploadInProgress, modifier = Modifier.weight(1f)) { Text("Add document") }
                            }
                        }
                    }
                    val structuredUrls = member.documents.mapTo(mutableSetOf()) { it.url }
                    val legacyUrls = member.documentUrls.filterNot(structuredUrls::contains)
                    if (member.documents.isNotEmpty() || legacyUrls.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        member.documents.forEach { document ->
                            TextButton(onClick = {
                                message = "Authorizing document access…"
                                repository.resolveDocumentUrl(document.url) { result ->
                                    result.fold(
                                        onSuccess = { signedUrl ->
                                            message = null
                                            if (isImageDocument(document.contentType, document.name)) {
                                                previewDocumentImage = signedUrl to document.name.ifBlank { document.type.label }
                                            } else if (signedUrl.scheme == "content") {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW)
                                                            .setDataAndType(
                                                                signedUrl,
                                                                document.contentType.ifBlank { "*/*" }
                                                            )
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    )
                                                }.onFailure {
                                                    message = "No app is available to open this document"
                                                }
                                            } else {
                                                uriHandler.openUri(signedUrl.toString())
                                            }
                                        },
                                        onFailure = { message = it.localizedMessage ?: "Unable to open document" }
                                    )
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text(document.name.ifBlank { document.type.label }, textAlign = TextAlign.Start)
                                    Text(
                                        listOfNotNull(
                                            document.type.label,
                                            document.sizeBytes.takeIf { it > 0 }?.toReadableSize()
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        legacyUrls.forEachIndexed { index, url ->
                            TextButton(onClick = {
                                message = "Authorizing document access…"
                                repository.resolveDocumentUrl(url) { result ->
                                    result.fold(
                                        onSuccess = { signedUrl ->
                                            message = null
                                            if (isImageDocument("", url)) {
                                                previewDocumentImage = signedUrl to "Document ${index + 1}"
                                            } else {
                                                uriHandler.openUri(signedUrl.toString())
                                            }
                                        },
                                        onFailure = { message = it.localizedMessage ?: "Unable to open document" }
                                    )
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Open legacy document ${index + 1}", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("No documents uploaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val memberEntitlements = repository.entitlements.filter { it.memberId == memberId }
            val memberCommitments = repository.commitments.filter { it.memberId == memberId }
            if (memberEntitlements.isNotEmpty() || memberCommitments.isNotEmpty()) {
                item {
                    SectionHeading("Benefits & commitments")
                    AppCard {
                        memberEntitlements.forEach { entitlement ->
                            val service = repository.service(entitlement.serviceId)
                            InfoRow(service?.name ?: "Service", "${entitlement.remainingQuantity} of ${entitlement.allocatedQuantity} ${service?.unitLabel ?: "uses"} left")
                        }
                        memberCommitments.forEach { commitment -> InfoRow(commitment.title, "${commitment.status.lowercase().replaceFirstChar { it.uppercase() }} • until ${commitment.validUntil.toUiDate()}", last = commitment == memberCommitments.last()) }
                    }
                }
            }
            item {
                SectionHeading("Payment history")
                val payments = repository.paymentsFor(memberId)
                if (payments.isEmpty()) AppCard { Text("No payments recorded", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else AppCard {
                    payments.take(4).forEachIndexed { index, payment ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialIcon(repository.organization.currencySymbol)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(payment.receiptNumber, style = MaterialTheme.typography.labelLarge)
                                Text("${payment.method.pretty()} • ${payment.paidOn.toUiDateTime()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${repository.organization.currencySymbol}${payment.amount.asMoney()}", fontWeight = FontWeight.Bold)
                        }
                        if (index != payments.take(4).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
            item {
                SectionHeading("Communication history")
                val history = repository.communicationsFor(memberId)
                if (history.isEmpty()) {
                    AppCard { Text("No communications sent yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    AppCard {
                        history.take(12).forEachIndexed { index, communication ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                InitialIcon(communication.channel.name, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(communication.title, style = MaterialTheme.typography.titleSmall)
                                    Text(communication.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${communication.channel.pretty()} • ${communication.status.lowercase().replaceFirstChar(Char::uppercase)} • ${communication.sentAt.toUiDateTime()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (index != history.take(12).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
            item {
                SectionHeading("Activity timeline")
                AppCard {
                    val events = repository.activitiesFor(memberId)
                    if (events.isEmpty()) Text("No activity yet")
                    events.take(8).forEachIndexed { index, event -> TimelineRow(event, index == events.take(8).lastIndex) }
                }
            }
        }
    }
}

private val MEMBER_DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/jpeg",
    "image/png",
    "image/webp"
)

@Composable
private fun ProfilePhotoSourceDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Camera", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                TextButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Gallery", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun createCameraPhotoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "captured-photos").apply { mkdirs() }
    val file = File.createTempFile("profile-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun Int.toReadableSize(): String = toLong().toReadableSize()

private fun Long.toReadableSize(): String = if (this >= 1024 * 1024) {
    String.format(Locale.getDefault(), "%.1f MB", this / (1024.0 * 1024.0))
} else {
    "${(this / 1024.0).coerceAtLeast(0.1).let { String.format(Locale.getDefault(), "%.1f", it) }} KB"
}

private fun PreparedUpload.savingsLabel(): String =
    if (savedPercent > 0) " • $savedPercent% smaller" else ""

private data class CustomFieldDraft(val id: String = UUID.randomUUID().toString(), val key: String = "", val value: String = "")

private fun List<CustomFieldDraft>.toCustomValues(): Map<String, String> =
    filter { it.key.isNotBlank() && it.value.isNotBlank() }.associate { it.key.trim() to it.value.trim() }

private fun parseOptionalDate(value: String): LocalDate? =
    value.trim().takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@Composable
private fun CustomFieldsEditor(fields: MutableList<CustomFieldDraft>, showErrors: Boolean) {
    val normalizedKeys = fields.map { it.key.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank)
    fields.forEachIndexed { index, field ->
        val incomplete = field.key.isBlank() != field.value.isBlank()
        val duplicate = field.key.isNotBlank() && normalizedKeys.count { it == field.key.trim().lowercase(Locale.ROOT) } > 1
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        field.key, { fields[index] = field.copy(key = it) }, Modifier.fillMaxWidth(),
                        label = { Text("Field name") }, singleLine = true, isError = showErrors && (incomplete || duplicate)
                    )
                    OutlinedTextField(
                        field.value, { fields[index] = field.copy(value = it) }, Modifier.fillMaxWidth(),
                        label = { Text("Value") }, singleLine = true, isError = showErrors && incomplete
                    )
                    TextButton(onClick = { fields.removeAt(index) }, modifier = Modifier.align(Alignment.End)) { Text("Remove") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(field.key, { fields[index] = field.copy(key = it) }, Modifier.weight(1f), label = { Text("Field name") }, singleLine = true, isError = showErrors && (incomplete || duplicate))
                    OutlinedTextField(field.value, { fields[index] = field.copy(value = it) }, Modifier.weight(1f), label = { Text("Value") }, singleLine = true, isError = showErrors && incomplete)
                    TextButton(onClick = { fields.removeAt(index) }) { Text("Remove") }
                }
            }
        }
        if (showErrors && duplicate) Text("Custom field names must be unique", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (index != fields.lastIndex) Spacer(Modifier.height(8.dp))
    }
    TextButton(onClick = { if (fields.size < 20) fields.add(CustomFieldDraft()) }, enabled = fields.size < 20) {
        Text("+ Add custom field")
    }
}

@Composable
private fun EditMemberDialog(repository: MemboraRepository, member: Member, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var firstName by rememberSaveable(member.id) { mutableStateOf(member.firstName) }
    var lastName by rememberSaveable(member.id) { mutableStateOf(member.lastName) }
    var mobile by rememberSaveable(member.id) { mutableStateOf(member.mobile) }
    var email by rememberSaveable(member.id) { mutableStateOf(member.email) }
    var alternatePhone by rememberSaveable(member.id) { mutableStateOf(member.alternatePhone) }
    var gender by rememberSaveable(member.id) { mutableStateOf(member.gender) }
    var dateOfBirth by rememberSaveable(member.id) { mutableStateOf(member.dateOfBirth?.toString().orEmpty()) }
    var addressLine by rememberSaveable(member.id) { mutableStateOf(member.addressLine) }
    var city by rememberSaveable(member.id) { mutableStateOf(member.city) }
    var postalCode by rememberSaveable(member.id) { mutableStateOf(member.postalCode) }
    var notes by rememberSaveable(member.id) { mutableStateOf(member.notes) }
    var emergencyName by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactName) }
    var emergencyPhone by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactPhone) }
    var emergencyRelationship by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactRelationship) }
    var error by rememberSaveable(member.id) { mutableStateOf<String?>(null) }
    val customFields = remember(member.id) {
        mutableStateListOf(*member.customValues.map { CustomFieldDraft(key = it.key, value = it.value) }.toTypedArray())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit member profile") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Personal information", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(firstName, { firstName = it }, Modifier.fillMaxWidth(), label = { Text("First name *") }, singleLine = true)
                    OutlinedTextField(lastName, { lastName = it }, Modifier.fillMaxWidth(), label = { Text("Last name") }, singleLine = true)
                    OutlinedTextField(gender, { gender = it }, Modifier.fillMaxWidth(), label = { Text("Gender") }, singleLine = true)
                    OutlinedTextField(dateOfBirth, { dateOfBirth = it }, Modifier.fillMaxWidth(), label = { Text("Date of birth") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
                }
                item {
                    HorizontalDivider()
                    Text("Contact & address", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text("Mobile number *") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                    OutlinedTextField(alternatePhone, { alternatePhone = it }, Modifier.fillMaxWidth(), label = { Text("Alternate phone") }, singleLine = true)
                    OutlinedTextField(addressLine, { addressLine = it }, Modifier.fillMaxWidth(), label = { Text("Street address") })
                    OutlinedTextField(city, { city = it }, Modifier.fillMaxWidth(), label = { Text("City") }, singleLine = true)
                    OutlinedTextField(postalCode, { postalCode = it }, Modifier.fillMaxWidth(), label = { Text("Postal code") }, singleLine = true)
                }
                item {
                    HorizontalDivider()
                    Text("Emergency contact", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(emergencyName, { emergencyName = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(emergencyRelationship, { emergencyRelationship = it }, Modifier.fillMaxWidth(), label = { Text("Relationship") }, singleLine = true)
                    OutlinedTextField(emergencyPhone, { emergencyPhone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }, singleLine = true)
                }
                item {
                    HorizontalDivider()
                    Text("Custom fields", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    CustomFieldsEditor(customFields, showErrors = error != null)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedDate = parseOptionalDate(dateOfBirth)
                val normalizedKeys = customFields.map { it.key.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank)
                error = when {
                    dateOfBirth.isNotBlank() && parsedDate == null -> "Use YYYY-MM-DD for the date of birth"
                    customFields.any { it.key.isBlank() != it.value.isBlank() } -> "Complete or remove each custom field"
                    normalizedKeys.size != normalizedKeys.distinct().size -> "Custom field names must be unique"
                    else -> repository.updateMemberProfile(
                        memberId = member.id,
                        firstName = firstName,
                        lastName = lastName,
                        mobile = mobile,
                        email = email,
                        alternatePhone = alternatePhone,
                        gender = gender,
                        dateOfBirth = parsedDate,
                        addressLine = addressLine,
                        city = city,
                        postalCode = postalCode,
                        notes = notes,
                        emergencyContactName = emergencyName,
                        emergencyContactPhone = emergencyPhone,
                        emergencyContactRelationship = emergencyRelationship,
                        customValues = customFields.toCustomValues()
                    ).exceptionOrNull()?.localizedMessage
                }
                if (error == null) onSaved()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
        actions = { BrandMark(Modifier.padding(end = 16.dp), small = true) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun FormSectionTitle(number: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.38f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
private fun PaymentDialog(repository: MemboraRepository, memberId: String, membershipId: String?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val outstanding = membershipId?.let(repository::outstandingBalance) ?: 0.0
    var amount by rememberSaveable { mutableStateOf(outstanding.takeIf { it > 0 }?.asMoney().orEmpty()) }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.UPI) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val parsedAmount = amount.replace(",", "").toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true)
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.BANK_TRANSFER).forEach { item -> FilterChip(selected = item == method, onClick = { method = item }, label = { Text(item.pretty()) }) }
                }
                if (membershipId != null) Text("Outstanding: ${repository.organization.currencySymbol}${outstanding.asMoney()}", style = MaterialTheme.typography.labelLarge)
                Text("A numbered receipt will be created automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paymentId = runCatching { repository.recordPayment(memberId, membershipId, parsedAmount, method) }
                        .onFailure { error = it.localizedMessage }
                        .getOrNull()
                    if (paymentId != null) onSaved(paymentId)
                },
                enabled = parsedAmount > 0 && (membershipId == null || parsedAmount <= outstanding)
            ) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenewDialog(repository: MemboraRepository, memberId: String, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val activePlans = repository.plans.filter { it.active }
    var selected by rememberSaveable { mutableStateOf(activePlans.firstOrNull()?.id) }
    val current = repository.membershipFor(memberId)
    val suggestedStart = if (current != null && current.status != MembershipStatus.CANCELLED && !current.endDate.isBefore(repository.currentDate())) current.endDate.plusDays(1) else repository.currentDate()
    var startDate by rememberSaveable { mutableStateOf(suggestedStart.toString()) }
    var endDate by rememberSaveable { mutableStateOf(selected?.let { repository.calculateMembershipEndDate(it, suggestedStart) }?.toString().orEmpty()) }
    var payment by rememberSaveable { mutableStateOf("") }
    var paymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.UPI) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew membership") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                item { Text("Choose a plan and confirm the membership term. A renewal can start today or after the current term.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Spacer(Modifier.height(10.dp)) }
                items(activePlans, key = { it.id }) { plan ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = plan.id
                        parseOptionalDate(startDate)?.let { start -> endDate = repository.calculateMembershipEndDate(plan.id, start)?.toString().orEmpty() }
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == plan.id, onClick = null)
                        Column(Modifier.weight(1f)) { Text(plan.name, style = MaterialTheme.typography.titleMedium); Text("${plan.durationValue} ${plan.durationUnit.lowercase()}", style = MaterialTheme.typography.labelMedium) }
                        Text("${repository.organization.currencySymbol}${(plan.price + plan.registrationFee).asMoney()}")
                    }
                }
                item {
                    OutlinedTextField(startDate, {
                        startDate = it
                        parseOptionalDate(it)?.let { start -> selected?.let { planId -> endDate = repository.calculateMembershipEndDate(planId, start)?.toString().orEmpty() } }
                    }, Modifier.fillMaxWidth(), label = { Text("Start date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(endDate, { endDate = it }, Modifier.fillMaxWidth(), label = { Text("Expiry date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(payment, { payment = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, Modifier.fillMaxWidth(), label = { Text("Payment now (optional)") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.BANK_TRANSFER).forEach { method ->
                            FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { Text(method.pretty()) })
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { Button(onClick = {
            val planId = selected
            val start = parseOptionalDate(startDate)
            val end = parseOptionalDate(endDate)
            var renewalId: String? = null
            error = when {
                planId == null -> "Select a membership plan"
                start == null || end == null -> "Enter valid dates using YYYY-MM-DD"
                end.isBefore(start) -> "Expiry date cannot be before the start date"
                else -> runCatching {
                    repository.renewMembership(memberId, planId, start, end, payment.replace(",", "").toDoubleOrNull() ?: 0.0, paymentMethod)
                }.fold(
                    onSuccess = { renewalId = it; null },
                    onFailure = { it.localizedMessage }
                )
            }
            if (error == null && renewalId != null) onSaved(renewalId!!)
        }, enabled = activePlans.isNotEmpty()) { Text("Create renewal") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FreezeMembershipDialog(repository: MemboraRepository, membership: Membership, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var until by rememberSaveable { mutableStateOf(repository.currentDate().plusDays(30).toString()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Freeze membership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Access is paused immediately. When resumed, the expiry date is extended by the actual frozen days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(until, { until = it }, Modifier.fillMaxWidth(), label = { Text("Planned freeze end") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("Reason") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = {
            val parsedUntil = parseOptionalDate(until)
            error = if (until.isNotBlank() && parsedUntil == null) "Use YYYY-MM-DD for the freeze end date"
            else repository.freezeMembership(membership.id, parsedUntil, reason).exceptionOrNull()?.localizedMessage
            if (error == null) onSaved()
        }) { Text("Freeze") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep active") } }
    )
}

@Composable
private fun CancelMembershipDialog(repository: MemboraRepository, membership: Membership, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel membership?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cancellation stops member access immediately. Existing payment and membership history will be retained.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("Cancellation reason *") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = {
            error = repository.cancelMembership(membership.id, reason).exceptionOrNull()?.localizedMessage
            if (error == null) onSaved()
        }, enabled = reason.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Cancel membership") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep membership") } }
    )
}

private fun isImageDocument(contentType: String, nameOrUrl: String): Boolean {
    if (contentType.startsWith("image/", ignoreCase = true)) return true
    val path = nameOrUrl.substringBefore('?').lowercase(Locale.ROOT)
    return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif")
        .any(path::endsWith)
}
