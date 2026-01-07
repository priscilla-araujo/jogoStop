package com.example.jogostop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jogostop.ui.theme.JogoStopTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JogoStopTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNav(navController)
                }
            }
        }
    }
}

/* --------------------------------- ROTAS --------------------------------- */

private object Routes {
    const val Login = "login"
    const val Register = "register"
    const val Home = "home"
    const val Instructions = "instructions"
    const val Setup = "setup"
    const val Game = "game"
    const val GameOver = "gameOver"
}

/* ----------------------------- MODELOS / LÓGICA ----------------------------- */

private val LettersPT = listOf(
    "A","B","C","D","E","F","G","H","I","J","L","M","N","O","P","Q","R","S","T","U","V","X","Z","Ç"
)

data class Player(val name: String, val eliminated: Boolean = false)

data class GameState(
    val category: String = "Animais",
    val players: List<Player> = emptyList(),
    val currentIndex: Int = 0,
    val currentLetter: String? = null,
    val usedWords: Set<String> = emptySet(),
    val lastWord: String? = null,
    val isOver: Boolean = false,
    val winnerName: String? = null,
)

private fun nextActiveIndex(players: List<Player>, startFrom: Int): Int? {
    if (players.isEmpty()) return null
    var idx = startFrom
    repeat(players.size) {
        idx = (idx + 1) % players.size
        if (!players[idx].eliminated) return idx
    }
    return null
}

private fun countAlive(players: List<Player>) = players.count { !it.eliminated }

private fun computeWinner(players: List<Player>): String? {
    val alive = players.filter { !it.eliminated }
    return if (alive.size == 1) alive.first().name else null
}

/* --------------------------------- CORES “DIVERTIDAS” --------------------------------- */

private val FunBlue = Color(0xFF00C6FF)
private val FunCyan = Color(0xFF00FFD1)
private val FunPink = Color(0xFFFF2E93)
private val FunOrange = Color(0xFFFFB300)
private val FunPurple = Color(0xFF7C4DFF)
private val FunGreen = Color(0xFF22C55E)
private val FunRed = Color(0xFFFF3B30)

private val FunPalette = listOf(FunBlue, FunCyan, FunPink, FunOrange, FunPurple, FunGreen)

private fun categoryColor(category: String): Color = when (category) {
    "Animais" -> FunGreen
    "Países" -> FunBlue
    "Comidas" -> FunOrange
    "Profissões" -> FunPurple
    "Filmes" -> FunPink
    "Marcas" -> FunCyan
    "Esportes" -> FunRed
    else -> FunBlue
}

/* --------------------------------- NAV --------------------------------- */

@Composable
fun AppNav(navController: NavHostController) {

    var selectedCategory by rememberSaveable { mutableStateOf("Animais") }

    // ✅ não usar rememberSaveable com data class custom sem Saver
    var gameState by remember { mutableStateOf(GameState(category = selectedCategory)) }

    NavHost(
        navController = navController,
        startDestination = Routes.Login
    ) {

        composable(Routes.Login) {
            LoginScreen(
                onLogin = { _, _ ->
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Routes.Register) }
            )
        }

        composable(Routes.Register) {
            RegisterScreen(
                onRegister = { _, _, _ -> navController.popBackStack() },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.Home) {
            HomeScreen(
                category = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                onOpenInstructions = { navController.navigate(Routes.Instructions) },
                onPlay = {
                    gameState = GameState(category = selectedCategory)
                    navController.navigate(Routes.Setup)
                }
            )
        }

        composable(Routes.Instructions) {
            InstructionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.Setup) {
            SetupScreen(
                category = selectedCategory,
                onBack = { navController.popBackStack() },
                onStart = { names ->
                    val players = names.filter { it.isNotBlank() }.map { Player(it.trim()) }
                    gameState = GameState(category = selectedCategory, players = players)
                    navController.navigate(Routes.Game) {
                        popUpTo(Routes.Setup) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Game) {
            GameScreen(
                state = gameState,
                onStateChange = { gameState = it },
                onExit = { navController.popBackStack(Routes.Home, false) },
                onGameOver = { navController.navigate(Routes.GameOver) }
            )
        }

        composable(Routes.GameOver) {
            GameOverScreen(
                winner = gameState.winnerName ?: "Vencedor",
                category = gameState.category,
                onRestart = {
                    gameState = GameState(category = selectedCategory)
                    navController.popBackStack(Routes.Home, false)
                }
            )
        }
    }
}

/* ----------------------------- UI: FUNDO / CARD ----------------------------- */

@Composable
private fun FunBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        FunPink.copy(alpha = 0.85f),
                        FunOrange.copy(alpha = 0.85f),
                        FunCyan.copy(alpha = 0.85f),
                        FunBlue.copy(alpha = 0.85f)
                    )
                )
            ),
        content = content
    )
}

@Composable
private fun GameCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = FunBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4B5563))
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/* ----------------------------- LOGIN / REGISTER ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (email: String, senha: String) -> Unit,
    onGoToRegister: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("JogoStop") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "🎉 Bem-vindo(a)!",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Entre pra jogar um STOP mais divertido ✨",
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(Modifier.height(14.dp))

                GameCard(
                    title = "Login",
                    subtitle = "Acesse sua conta para começar",
                    accent = FunCyan
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; erro = null },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it; erro = null },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(erro != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(text = erro.orEmpty(), color = FunRed, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            when {
                                email.isBlank() -> erro = "Informe o email."
                                senha.isBlank() -> erro = "Informe a senha."
                                else -> onLogin(email.trim(), senha)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FunPink)
                    ) {
                        Text("Entrar", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = onGoToRegister,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Não tem conta? Cadastre-se", color = FunPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (nome: String, email: String, senha: String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var confirmar by rememberSaveable { mutableStateOf("") }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Cadastro") },
                navigationIcon = { TextButton(onClick = onBackToLogin) { Text("Voltar", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Spacer(Modifier.height(8.dp))

                GameCard(
                    title = "Criar conta",
                    subtitle = "Rapidinho e sem complicação 😄",
                    accent = FunOrange
                ) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it; erro = null },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; erro = null },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it; erro = null },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmar,
                        onValueChange = { confirmar = it; erro = null },
                        label = { Text("Confirmar senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(erro != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(text = erro.orEmpty(), color = FunRed, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            when {
                                nome.isBlank() -> erro = "Informe seu nome."
                                email.isBlank() -> erro = "Informe seu email."
                                senha.length < 6 -> erro = "A senha deve ter pelo menos 6 caracteres."
                                senha != confirmar -> erro = "As senhas não conferem."
                                else -> onRegister(nome.trim(), email.trim(), senha)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FunOrange)
                    ) {
                        Text("Cadastrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ----------------------------- HOME / CATEGORIAS ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    category: String,
    onCategoryChange: (String) -> Unit,
    onOpenInstructions: () -> Unit,
    onPlay: () -> Unit
) {
    val categories = listOf("Animais", "Países", "Comidas", "Profissões", "Filmes", "Marcas", "Esportes")
    val accent = categoryColor(category)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Categorias") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))

                GameCard(
                    title = "Escolha a categoria",
                    subtitle = "Depois é só chamar a galera e jogar!",
                    accent = accent
                ) {
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Categoria selecionada") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = { onCategoryChange(c); expanded = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Botões “tipo game”
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onOpenInstructions,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunPurple)
                        ) { Text("Instruções", fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = onPlay,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) { Text("Jogar", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/* ----------------------------- INSTRUÇÕES (PÁGINAS) ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(onBack: () -> Unit) {
    val pages = listOf(
        "1) Preparação:\nColoque o celular no centro. Todos ao redor. Escolha uma categoria.",
        "2) Turnos:\nO primeiro turno é de quem tocar primeiro. Depois passa para a direita.",
        "3) Palavras:\nAntes de escolher a letra, diga uma palavra da categoria que comece com a letra.",
        "4) Erros:\nSe não conseguir dizer uma palavra válida ou repetir uma já dita, perde e é eliminado.",
        "5) Consenso:\nSe alguém discordar, pause e votem. Quem perder a votação é eliminado.",
        "6) Vencedor:\nEliminados saem até restar 1. O último é o campeão!"
    )

    var index by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Instruções") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Voltar", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                GameCard(
                    title = "Passo ${index + 1}",
                    subtitle = "Use os botões para navegar",
                    accent = FunCyan
                ) {
                    Text(pages[index], style = MaterialTheme.typography.bodyLarge)

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { if (index > 0) index-- },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunBlue)
                        ) { Text("Anterior", fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = { if (index < pages.lastIndex) index++ },
                            enabled = index < pages.lastIndex,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunPink)
                        ) { Text("Próximo", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/* ----------------------------- SETUP (JOGADORES) ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    category: String,
    onBack: () -> Unit,
    onStart: (names: List<String>) -> Unit
) {
    val accent = categoryColor(category)

    var qtd by rememberSaveable { mutableIntStateOf(3) }
    var names by rememberSaveable { mutableStateOf(List(3) { "" }) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    fun syncList(newQtd: Int) {
        qtd = newQtd.coerceIn(2, 10)
        names =
            if (names.size == qtd) names
            else if (names.size < qtd) names + List(qtd - names.size) { "" }
            else names.take(qtd)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Jogadores") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Voltar", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Spacer(Modifier.height(8.dp))

                GameCard(
                    title = "Configurar partida",
                    subtitle = "Categoria: $category",
                    accent = accent
                ) {
                    Text("Quantidade: $qtd", fontWeight = FontWeight.Black)
                    Slider(
                        value = qtd.toFloat(),
                        onValueChange = { syncList(it.toInt()) },
                        valueRange = 2f..10f,
                        steps = 7
                    )

                    Spacer(Modifier.height(10.dp))

                    names.forEachIndexed { i, v ->
                        OutlinedTextField(
                            value = v,
                            onValueChange = { value ->
                                erro = null
                                names = names.toMutableList().apply { this[i] = value }
                            },
                            label = { Text("Jogador ${i + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    AnimatedVisibility(erro != null) {
                        Text(erro.orEmpty(), color = FunRed, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val cleaned = names.map { it.trim() }.filter { it.isNotBlank() }
                            if (cleaned.size < 2) erro = "Informe pelo menos 2 nomes."
                            else onStart(cleaned)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) { Text("Começar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/* ----------------------------- JOGO (TIMER + VISUAL GAME) ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onStateChange: (GameState) -> Unit,
    onExit: () -> Unit,
    onGameOver: () -> Unit
) {
    var word by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // ⏱️ timer de 20s
    var timeLeft by rememberSaveable { mutableIntStateOf(20) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }

    // consenso/votação
    var showConsensus by rememberSaveable { mutableStateOf(false) }
    var voteYes by rememberSaveable { mutableIntStateOf(0) }
    var voteNo by rememberSaveable { mutableIntStateOf(0) }

    val players = state.players
    val aliveCount = countAlive(players)
    val currentPlayer = players.getOrNull(state.currentIndex)
    val accent = categoryColor(state.category)

    // cor do “disco” da letra
    var letterColor by remember { mutableStateOf(FunPink) }

    fun updateAndCheckOver(newPlayers: List<Player>, nextIndexFrom: Int) {
        val winner = computeWinner(newPlayers)
        if (winner != null) {
            onStateChange(state.copy(players = newPlayers, isOver = true, winnerName = winner))
            onGameOver()
            return
        }

        val nextIdx = nextActiveIndex(newPlayers, nextIndexFrom) ?: 0
        onStateChange(
            state.copy(
                players = newPlayers,
                currentIndex = nextIdx,
                currentLetter = null,
                lastWord = null
            )
        )
        timerRunning = false
        timeLeft = 20
        word = ""
        error = null
    }

    fun eliminateCurrent(reason: String) {
        timerRunning = false
        val newPlayers = players.mapIndexed { i, p ->
            if (i == state.currentIndex) p.copy(eliminated = true) else p
        }
        error = "Eliminado: ${currentPlayer?.name ?: ""} ($reason)"
        updateAndCheckOver(newPlayers, state.currentIndex)
    }

    fun spinLetter() {
        val letter = LettersPT.random(Random(System.currentTimeMillis()))
        letterColor = FunPalette.random()
        onStateChange(state.copy(currentLetter = letter, lastWord = null))
        word = ""
        error = null
    }

    fun submitWord() {
        val letter = state.currentLetter
        if (letter == null) {
            error = "Gire a letra primeiro."
            return
        }

        // tempo acabou
        if (timeLeft <= 0) {
            eliminateCurrent("tempo esgotado")
            return
        }

        val w = word.trim()
        if (w.isBlank()) {
            // regra: se clicar confirmar sem palavra perde
            eliminateCurrent("não falou palavra")
            return
        }

        val first = w.first().uppercaseChar().toString()
        if (first != letter) {
            eliminateCurrent("não começou com '$letter'")
            return
        }

        val normalized = w.lowercase()
        if (state.usedWords.contains(normalized)) {
            eliminateCurrent("repetiu palavra")
            return
        }

        timerRunning = false
        val newUsed = state.usedWords + normalized
        val nextIdx = nextActiveIndex(players, state.currentIndex) ?: state.currentIndex
        onStateChange(
            state.copy(
                usedWords = newUsed,
                lastWord = w,
                currentIndex = nextIdx,
                currentLetter = null
            )
        )
        word = ""
        error = null
    }

    // ⏱️ começa quando a letra aparece
    LaunchedEffect(state.currentLetter, state.currentIndex) {
        if (state.currentLetter != null && !state.isOver) {
            timeLeft = 20
            timerRunning = true

            while (timerRunning && timeLeft > 0) {
                delay(1000)
                timeLeft--
            }

            if (timerRunning && timeLeft == 0) {
                eliminateCurrent("tempo esgotado")
                timerRunning = false
            }
        } else {
            timerRunning = false
            timeLeft = 20
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("STOP • ${state.category}") },
                navigationIcon = { TextButton(onClick = onExit) { Text("Sair", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                actions = {
                    AssistChip(
                        onClick = { showConsensus = true; voteYes = 0; voteNo = 0 },
                        label = { Text("Pausar") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.9f),
                            labelColor = Color.Black
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Spacer(Modifier.height(6.dp))

                GameCard(
                    title = "Vez de: ${currentPlayer?.name ?: "-"}",
                    subtitle = "Vivos: $aliveCount",
                    accent = accent
                ) {
                    // LETRA em “disco”
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF8FAFC)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(6.dp, letterColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.currentLetter ?: "—",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.headlineLarge
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                val timerTextColor =
                                    if (state.currentLetter == null) Color(0xFF6B7280)
                                    else if (timeLeft <= 5) FunRed
                                    else FunBlue

                                Text(
                                    text = if (state.currentLetter == null) "⏱️ 20s" else "⏱️ ${timeLeft}s",
                                    color = timerTextColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { spinLetter() },
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunPink)
                        ) {
                            Text("Girar\nLetra", textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it; error = null },
                        label = { Text("Palavra da categoria") },
                        placeholder = { Text("Ex: Foca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { eliminateCurrent("desistiu") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunOrange)
                        ) { Text("Perdi", fontWeight = FontWeight.Black) }

                        Button(
                            onClick = { submitWord() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunGreen)
                        ) { Text("Confirmar", fontWeight = FontWeight.Black) }
                    }

                    AnimatedVisibility(state.lastWord != null) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "✅ Última aceita: ${state.lastWord}",
                                color = Color(0xFF111827),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(error != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(error.orEmpty(), color = FunRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                GameCard(
                    title = "Jogadores",
                    subtitle = "Quem ainda tá no jogo?",
                    accent = FunCyan
                ) {
                    players.forEach { p ->
                        val status = if (p.eliminated) "Eliminado" else "No jogo"
                        val color = if (p.eliminated) FunRed else FunGreen
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(status, color = color, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showConsensus) {
            AlertDialog(
                onDismissRequest = { showConsensus = false },
                title = { Text("Consenso / Votação") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Use quando alguém discordar da palavra.\n" +
                                    "SIM = elimina o jogador atual.\n" +
                                    "NÃO = mantém o jogador."
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { voteNo++ },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FunBlue)
                            ) { Text("NÃO ($voteNo)", fontWeight = FontWeight.Black) }

                            Button(
                                onClick = { voteYes++ },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FunPink)
                            ) { Text("SIM ($voteYes)", fontWeight = FontWeight.Black) }
                        }
                        Text("Finalize quando decidir.", color = Color(0xFF6B7280))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConsensus = false
                            if (voteYes > voteNo) {
                                eliminateCurrent("perdeu a votação")
                            } else {
                                timerRunning = false
                                val nextIdx = nextActiveIndex(players, state.currentIndex) ?: state.currentIndex
                                onStateChange(state.copy(currentIndex = nextIdx, currentLetter = null, lastWord = null))
                                word = ""
                                error = "Votação: jogador mantido."
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FunGreen)
                    ) { Text("Finalizar", fontWeight = FontWeight.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { showConsensus = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

/* ----------------------------- GAME OVER ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverScreen(
    winner: String,
    category: String,
    onRestart: () -> Unit
) {
    val accent = categoryColor(category)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Fim de jogo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        FunBackground(Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameCard(
                    title = "GAME OVER",
                    subtitle = "Categoria: $category",
                    accent = accent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🏆 $winner",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onRestart,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Voltar ao início", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/* ----------------------------- PREVIEW ----------------------------- */

@Preview(showBackground = true)
@Composable
fun PreviewLogin() {
    JogoStopTheme {
        LoginScreen(onLogin = { _, _ -> }, onGoToRegister = {})
    }
}
