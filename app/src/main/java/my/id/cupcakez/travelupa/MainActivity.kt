package my.id.cupcakez.travelupa

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import my.id.cupcakez.travelupa.ui.theme.TravelupaTheme
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class Screen(val route: String){
    object Greeting: Screen("greeting")
    object Login: Screen("login")
    object Register: Screen("register")
    object RekomendasiTempat: Screen("rekomendasi_tempat")
}

class MainActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var imageDao: ImageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "travelupa-database"
        ).build()
        imageDao = db.imageDao()

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                Surface (
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ){
                    AppNavigation(
                        currentUser,
                        firestore,
                        storage,
                        imageDao,
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    currentUser: FirebaseUser?,
    firestore: FirebaseFirestore,
    storage: FirebaseStorage,
    imageDao: ImageDao
){
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route
    ){
        composable(Screen.Greeting.route){
            GreetingScreen(
                onStart = {
                    navController.navigate(Screen.Login.route){
                        popUpTo(Screen.Greeting.route){
                            inclusive = true
                        }
                    }
                }
            )
        }
        composable(Screen.Login.route){
            LoginScreen (
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route){
                        popUpTo(Screen.Login.route){
                            inclusive = true
                        }
                    }
                },
                onRegister = {
                    navController.navigate(Screen.Register.route){
                        popUpTo(Screen.Login.route){
                            inclusive = true
                        }
                    }
                }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                firestore = firestore,
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                    }
                },
                onGallerySelected = {
                    navController.navigate("gallery")
                }
            )
        }
        composable("gallery") {
            GalleryScreen(
                imageDao = imageDao,
                onImageSelected = { uri ->
                    // Handle image selection
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Register.route){
            RegisterScreen (
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route){
                        popUpTo(Screen.Register.route){
                            inclusive = true
                        }
                    }
                },
                onLogin = {
                    navController.navigate(Screen.Login.route){
                        popUpTo(Screen.Register.route){
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun GreetingScreen(
    onStart: ()-> Unit
){
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.
            fillMaxWidth().
            padding(16.dp)
        ) {
            Text(
                text = "Selamat Datang di Travelupa!",
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Solusi buat kamu yang lupa kemana-mana",
                style = MaterialTheme.typography.h6,
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.
            width(360.dp).
            padding(bottom = 16.dp).
            align(Alignment.BottomCenter)
        ) {
            Text(text = "Mulai")
        }
    }
}

data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    var gambarUriString: String? = null,
    val gambarResId: Int? = null
)



@Composable
fun RekomendasiTempatScreen(
    firestore: FirebaseFirestore,
    onBackToLogin : (() -> Unit)? = null,
    onGallerySelected: () -> Unit
) {
    var daftarTempatWisata by remember {
        mutableStateOf(
            listOf<TempatWisata>()
        )
    }
    var showTambahDialog by remember { mutableStateOf(false) }
    var drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val tempatWisataList = mutableListOf<TempatWisata>()
        firestore.collection("tempat_wisata")
            .get()
            .addOnSuccessListener{ result->
                for (document in result){
                    val tempatWisata = document.toObject(TempatWisata::class.java)
                    tempatWisataList.add(tempatWisata)
                }
                daftarTempatWisata = tempatWisataList
            }
            .addOnFailureListener { exception ->
                Log.w("RekomendasiTempatScreen", "Error getting documents", exception)
            }
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column {
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()
                Text(
                    text = "Gallery",
                    modifier = Modifier.fillMaxWidth().clickable{
                        onGallerySelected()
                    }.padding(16.dp)
                )
                Text(
                    text = "Logout",
                    modifier = Modifier.fillMaxWidth().clickable{
                        coroutineScope.launch{
                            drawerState.close()
                        }
                        onBackToLogin?.invoke()
                    }.padding(16.dp)
                )
            }
        }
    ) {
        Scaffold (
            topBar = {
                TopAppBar(
                    title = {
                        Box(){
                            Text(
                                text = "Rekomendasi Tempat Wisata",
                                style = MaterialTheme.typography.h6,
                                color = Color.White
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showTambahDialog = true },
                    backgroundColor = MaterialTheme.colors.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah Tempat Wisata")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues).padding(16.dp)
            ) {
                LazyColumn {
                    items(daftarTempatWisata) { tempat ->
                        TempatItemEditable(
                            tempat = tempat,
                            onDelete = {
                                val updatedList = daftarTempatWisata.filter { it != tempat }
                                daftarTempatWisata = updatedList

                                // Query the document by name and delete it
                                firestore.collection("tempat_wisata")
                                    .whereEqualTo("nama", tempat.nama)
                                    .get()
                                    .addOnSuccessListener { querySnapshot ->
                                        if (!querySnapshot.isEmpty) {
                                            for (document in querySnapshot.documents) {
                                                firestore.collection("tempat_wisata")
                                                    .document(document.id)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        Log.d("RekomendasiTempatScreen", "Document successfully deleted!")
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e("RekomendasiTempatScreen", "Error deleting document", e)

                                                        // Revert the UI changes on failure
                                                        daftarTempatWisata = updatedList + tempat
                                                    }
                                            }
                                        } else {
                                            Log.w("RekomendasiTempatScreen", "No document found with the specified name")
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("RekomendasiTempatScreen", "Error fetching document", e)

                                        // Revert the UI changes on failure
                                        daftarTempatWisata = updatedList + tempat
                                    }
                            }
                        )
                    }
                }
            }

            if (showTambahDialog) {
                TambahTempatWisataDialog(
                    firestore = firestore,
                    context = LocalContext.current,
                    onDismiss = { showTambahDialog = false },
                    onTambah = { nama, deskripsi, gambarUri ->
                        val uriString = gambarUri?.toString() ?: ""
                        val nuevoTempat = TempatWisata(nama, deskripsi, uriString)
                        daftarTempatWisata = daftarTempatWisata + nuevoTempat
                        showTambahDialog = false
                    }
                )
            }
        }
    }
}


@Composable
fun TempatItemEditable(
    tempat: TempatWisata,
    onDelete: () -> Unit
){
    val firestore = FirebaseFirestore.getInstance()
    var expanded by remember { mutableStateOf(false) }

    Card (
        modifier = Modifier.
            fillMaxWidth().
            padding(vertical = 8.dp).
            background(MaterialTheme.colors.surface),
        elevation = 4.dp
    ){
        Column(modifier = Modifier.padding(16.dp)
        ){
            Image(
                painter = tempat.gambarUriString?.let{ uriString->
                    rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(uriString))
                            .build()
                    )
                } ?: tempat.gambarResId?.let{
                    painterResource(id = it)
                } ?: painterResource(id = R.drawable.default_image),
                contentDescription = tempat.nama,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ){
                Column (
                    modifier = Modifier.align(Alignment.CenterStart)
                ){
                    Text(
                        text = tempat.nama,
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
                    )

                    Text(
                        text = tempat.deskripsi,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                IconButton(
                    onClick = { expanded = true},
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colors.error
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    offset = DpOffset(250.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            firestore.collection("tempat_wisata").document(tempat.nama)
                                .delete()
                                .addOnSuccessListener{
                                    onDelete()
                                }
                                .addOnFailureListener{e->
                                    Log.w("TempatItemEditable", "Error deleting document", e)
                                }
                        }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}


fun uploadImageToFirestore(
    firestore: FirebaseFirestore,
    context: Context,
    imageUri: Uri,
    tempatWisata: TempatWisata,
    onSuccess: (TempatWisata) -> Unit,
    onFailure: (Exception) -> Unit
){
    val db = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "travelupa-database"
    ).build()

    val imageDao = db.imageDao()
    val localPath = saveImageLocally(context, imageUri)

    CoroutineScope(Dispatchers.IO).launch{
        val imageId = imageDao.insert(ImageEntity(localPath = localPath))
        val updatedTempatWisata = tempatWisata.copy(gambarUriString = localPath)

        firestore.collection("tempat_wisata")
            .add(updatedTempatWisata)
            .addOnSuccessListener {
                onSuccess(updatedTempatWisata)
            }
            .addOnFailureListener{ e->
                onFailure(e)
            }
    }
}

fun saveImageLocally(
    context: Context,
    uri: Uri
): String {
    try{
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "image_${System.currentTimeMillis()}.jpg")

        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d("ImageSave", "Image saved successfully to: ${file.absolutePath}")
        return file.absolutePath
    } catch (e: Exception){
        Log.e("ImageSave", "Error saving image", e)
        throw e
    }
}

@Composable
fun TambahTempatWisataDialog(
    firestore: FirebaseFirestore,
    context: Context,
    onDismiss : () -> Unit,
    onTambah : (String, String, String) -> Unit
){
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val gambarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) {
        uri: Uri? -> gambarUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Tempat Wisata Baru") },
        text = {
            Column {
                TextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Tempat") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Deskripsi") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )

                Spacer(modifier = Modifier.height(8.dp))

                gambarUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(model = uri),
                        contentDescription = "Gambar yang dipilih",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { gambarLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Text("Pilih Gambar")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isNotBlank() && deskripsi.isNotBlank() && gambarUri != null){
                        isUploading = true
                        val tempatWisata = TempatWisata(nama, deskripsi)

                        uploadImageToFirestore(
                            firestore,
                            context,
                            gambarUri!!,
                            tempatWisata,
                            onSuccess = { uploadedTempat ->
                                isUploading = false
                                // Here need to attenttion
                                onTambah(nama, deskripsi, uploadedTempat.gambarUriString.toString())
                                onDismiss()
                            },
                            onFailure = { e->
                                isUploading = false
                            }
                        )
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading){
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                }else{
                    Text("Tambah")
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface )
            ) {
                Text("Batal")
            }
        }

    )
}


@Composable
fun LoginScreen(
    onLoginSuccess: ()-> Unit,
    onRegister: ()-> Unit
){
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column (
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ){
        TextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = {
                        isPasswordVisible = !isPasswordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            onValueChange = { password = it
                errorMessage = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isBlank() && password.isBlank()){
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch{
                    try {
                        val authResult = withContext(Dispatchers.IO){
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                        }
                        isLoading = false
                        onLoginSuccess()
                    }catch (e: Exception){
                        isLoading = false
                        errorMessage = "Login failed: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading){
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            }else{
                Text("Login")
            }
        }

        Text(
            text = "Belum punya akun? Daftar disini",
            modifier = Modifier.clickable {
                onRegister()
            }
        )

        errorMessage?.let{
            Text(
                text = it,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}


@Composable
fun RegisterScreen(
    onRegisterSuccess: ()-> Unit,
    onLogin: ()-> Unit
){
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column (
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ){
        TextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = {
                        isPasswordVisible = !isPasswordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            onValueChange = { password = it
                errorMessage = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isBlank() && password.isBlank()){
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch{
                    try {
                        val authResult = withContext(Dispatchers.IO){
                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                        }
                        isLoading = false
                        onRegisterSuccess()
                    }catch (e: Exception){
                        isLoading = false
                        errorMessage = "Register failed: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading){
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            }else{
                Text("Register")
            }
        }

        Text(
            text = "Sudah punya akun? Login disini",
            modifier = Modifier.clickable {
                onLogin()
            }
        )

        errorMessage?.let{
            Text(
                text = it,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    imageDao: ImageDao,
    onImageSelected: (Uri) -> Unit,
    onBack: ()-> Unit
){
    val images by imageDao.getAllImages().collectAsState(initial = emptyList())
    var showAddImageDialog by remember { mutableStateOf(false) }
    var selectedImageEntity by remember { mutableStateOf<ImageEntity?>(null) }
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf<ImageEntity?>(null) }

    LaunchedEffect(images) {
        Log.d("GalleryScreen", "Total images: ${images.size}")
        images.forEachIndexed { index, image ->
            Log.d("GalleryScreen", "Image $index path: ${image.localPath}")
            val file = File(image.localPath)
            Log.d("GalleryScreen", "File exists: ${file.exists()}, is readable: ${file.canRead()}")
        }
    }

    Scaffold (
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddImageDialog = true },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Image")
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(paddingValues)
        ) {
            items(images) { image->
                Image(
                    painter = rememberAsyncImagePainter(
                        model = image.localPath
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp).padding(4.dp).clickable{
                        selectedImageEntity = image
                        onImageSelected(Uri.parse(image.localPath))
                    },
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showAddImageDialog){
            AddImageDialog(
                onDismiss = { showAddImageDialog = false },
                onImageAdded = { uri ->
                    try {
                        val localPath = saveImageLocally(context, uri)
                        val newImage = ImageEntity(localPath = localPath)
                        CoroutineScope(Dispatchers.IO).launch{
                            imageDao.insert(newImage)
                        }
                        showAddImageDialog = false
                    }catch (e: Exception){
                        Log.e("ImageSave", "Failed to save image", e)
                    }
                }
            )
        }

        selectedImageEntity?.let { imageEntity->
            ImageDetailDialog(
                imageEntity = imageEntity,
                onDismiss = { selectedImageEntity = null },
                onDelete = { imageToDelete ->
                    showDeleteConfirmation = imageToDelete
                }
            )
        }

        showDeleteConfirmation?.let{ imageToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = null },
                title = { Text("Delete Image") },
                text = { Text("Are you sure you want to delete this image?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch{
                                imageDao.delete(imageToDelete)
                                val file = File(imageToDelete.localPath)

                                if (file.exists()){
                                    file.delete()
                                }
                            }
                            showDeleteConfirmation = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmation = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AddImageDialog(
    onDismiss: () -> Unit,
    onImageAdded: (Uri) -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Launchers for image selection and taking a photo
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val uri = saveBitmapToUri(context, it)
            imageUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Image") },
        text = {
            Column {
                // Display the selected image
                imageUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(model = uri),
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Buttons for selecting or taking a photo
                Row {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select from File")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    imageUri?.let { uri -> onImageAdded(uri) }
                },
                enabled = imageUri != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ImageDetailDialog(
    imageEntity: ImageEntity,
    onDismiss: () -> Unit,
    onDelete: (ImageEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                painter = rememberAsyncImagePainter(model = imageEntity.localPath),
                contentDescription = "Detailed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        },
        confirmButton = {
            Row {
                Button(onClick = { onDelete(imageEntity) }) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {}
    )
}

fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
    val outputStream = FileOutputStream(file)

    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    outputStream.close()

    return Uri.fromFile(file)
}
