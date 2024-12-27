package my.id.cupcakez.travelupa
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import my.id.cupcakez.travelupa.ui.theme.TravelupaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TravelupaTheme {
                RekomendasiTempatScreen()
            }
        }
    }
}


@Composable
fun GreetingScreen(){
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
            onClick = { /*TODO*/ },
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
    val nama : String,
    val deskripsi : String,
    var gambar : Int
)

val daftarTempatWisata = listOf(
    TempatWisata("Tumpak Sewu", "Air terjun tercantik di Jawa Timur.", R.drawable.tumpak_sewu),
    TempatWisata("Gunung Bromo", "Matahari terbitnya bagus banget.", R.drawable.gunung_bromo),
    TempatWisata("Pantai Kuta", "Pantai yang indah di Bali.", R.drawable.pantai_kuta),
    TempatWisata("Pulau Komodo", "Pulau tempat tinggalnya komodo.", R.drawable.pulau_komodo),
    TempatWisata("Candi Borobudur", "Candi yang indah di Jawa Tengah.", R.drawable.candi_borobudur),
)

@Composable
fun RekomendasiTempatScreen(){
    LazyColumn (
        modifier = Modifier.padding(16.dp)
    ){
        items(daftarTempatWisata){
            tempat -> TempatItem(tempat)
        }
    }
}

@Composable
fun TempatItem(tempat : TempatWisata) {
    Card (
        modifier =  Modifier.
            fillMaxWidth().
            padding(vertical = 8.dp).
            background(MaterialTheme.colors.surface),
        elevation = 4.dp
    ){
        Column (
            modifier = Modifier.padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = tempat.gambar),
                contentDescription = tempat.nama,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop
            )
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
    }
}