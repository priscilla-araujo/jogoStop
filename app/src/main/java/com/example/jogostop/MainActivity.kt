package com.example.jogostop

// ----------------------------- IMPORTS -----------------------------
// Imports do Android / Compose / Material3 / Navigation / Coroutines
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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

// ===================================================================
// 1) MAIN ACTIVITY: PONTO DE ENTRADA DO APP
// ===================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enableEdgeToEdge: deixa a UI usar a área total da tela (inclui “edge”)
        enableEdgeToEdge()

        // setContent: começa o Jetpack Compose (UI declarativa)
        setContent {
            // Aplica o tema do app (cores, tipografia Material)
            JogoStopTheme {

                // NavController: controla as trocas de tela (Navigation Compose)
                val navController = rememberNavController()

                // Surface: “base” de UI com cor de fundo
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // AppNav: onde definimos todas as telas e rotas
                    AppNav(navController)
                }
            }
        }
    }
}

/* --------------------------------- ROTAS --------------------------------- */
// Rotas (strings) usadas no NavHost para navegar entre telas.
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

// Lista de letras em PT (sem K/W/Y, por exemplo) para o “sorteio”
private val LettersPT = listOf(
    "A","B","C","D","E","F","G","H","I","J","L","M","N","O","P","Q","R","S","T","U","V","X","Z"
)

// Modelo do jogador:
// - name: nome do jogador
// - eliminated: se foi eliminado (true/false)
data class Player(val name: String, val eliminated: Boolean = false)

// Registro do histórico de palavras aceitas:
// Guarda quem jogou, a letra e a palavra.
data class WordEntry(
    val playerName: String,
    val letter: String,
    val word: String
)

// Estado do jogo (GameState):
// É o “coração” da partida, guardando tudo que o jogo precisa saber.
data class GameState(
    val category: String = "Animais",
    val players: List<Player> = emptyList(),

    // Índice do jogador atual (quem está jogando agora)
    val currentIndex: Int = 0,

    // Letra sorteada do turno atual (null quando ainda não girou)
    val currentLetter: String? = null,

    // Set de palavras já usadas (normalizadas em lowercase) -> não repetir palavra
    val usedWords: Set<String> = emptySet(),

    // Histórico de palavras aceitas (fica até o fim da partida)
    val acceptedWords: List<WordEntry> = emptyList(),

    // Set de letras já sorteadas -> evita repetir letra durante o jogo
    val usedLetters: Set<String> = emptySet(),

    // Última palavra aceita (só para mostrar na UI)
    val lastWord: String? = null,

    // Controle de final de jogo
    val isOver: Boolean = false,
    val winnerName: String? = null,
)

// Próximo jogador “vivo” (não eliminado), a partir de startFrom.
// Faz loop circular na lista.
private fun nextActiveIndex(players: List<Player>, startFrom: Int): Int? {
    if (players.isEmpty()) return null
    var idx = startFrom
    repeat(players.size) {
        idx = (idx + 1) % players.size
        if (!players[idx].eliminated) return idx
    }
    return null // se todo mundo estiver eliminado, não tem próximo
}

// Conta quantos jogadores ainda estão vivos
private fun countAlive(players: List<Player>) = players.count { !it.eliminated }

// Se restar 1 jogador vivo, retorna o nome dele (vencedor). Senão, null.
private fun computeWinner(players: List<Player>): String? {
    val alive = players.filter { !it.eliminated }
    return if (alive.size == 1) alive.first().name else null
}

/* --------------------------------- CORES “DIVERTIDAS” --------------------------------- */
// Paleta de cores usadas no estilo do app (visual)
private val FunBlue = Color(0xFF00C6FF)
private val FunCyan = Color(0xFF00FFD1)
private val FunPink = Color(0xFFFF2E93)
private val FunOrange = Color(0xFFFFB300)
private val FunPurple = Color(0xFF7C4DFF)
private val FunGreen = Color(0xFF22C55E)
private val FunRed = Color(0xFFFF3B30)

// Lista para pegar cores aleatórias (ex: disco da letra)
private val FunPalette = listOf(FunBlue, FunCyan, FunPink, FunOrange, FunPurple, FunGreen)

// Cor por categoria (só para UI ficar “temática”)
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

// AppNav: controla as telas do app (Navigation Compose).
// Aqui definimos as rotas e o que cada rota mostra.
@Composable
fun AppNav(navController: NavHostController) {

    // Categoria selecionada na Home
    // rememberSaveable: sobrevive a recomposição e (em muitos casos) rotação.
    var selectedCategory by rememberSaveable { mutableStateOf("Animais") }

    // Estado do jogo (GameState)
    // ⚠️ Importante: NÃO usamos rememberSaveable aqui porque GameState é data class custom.
    // Para usar rememberSaveable, precisaríamos de um Saver (salvar/restaurar manual).
    var gameState by remember { mutableStateOf(GameState(category = selectedCategory)) }

    // NavHost: “mapa” de rotas
    NavHost(
        navController = navController,
        startDestination = Routes.Login
    ) {

        // ------------------ TELA LOGIN ------------------
        composable(Routes.Login) {
            LoginScreen(
                onLogin = { _, _ ->
                    // Ao logar: navega para Home e remove Login da pilha (não volta no “voltar”)
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Routes.Register) }
            )
        }

        // ------------------ TELA CADASTRO ------------------
        composable(Routes.Register) {
            RegisterScreen(
                // Cadastro aqui é “mock” (não grava em DB). Só volta.
                onRegister = { _, _, _ -> navController.popBackStack() },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // ------------------ HOME (CATEGORIAS) ------------------
        composable(Routes.Home) {
            HomeScreen(
                category = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                onOpenInstructions = { navController.navigate(Routes.Instructions) },
                onPlay = {
                    // Ao jogar: reseta GameState e vai para Setup (jogadores)
                    gameState = GameState(category = selectedCategory)
                    navController.navigate(Routes.Setup)
                }
            )
        }

        // ------------------ INSTRUÇÕES ------------------
        composable(Routes.Instructions) {
            InstructionsScreen(
                onBack = { navController.popBackStack() },
                onStartGame = { navController.navigate(Routes.Setup) }
            )
        }

        // ------------------ SETUP (JOGADORES) ------------------
        composable(Routes.Setup) {
            SetupScreen(
                category = selectedCategory,
                onBack = { navController.popBackStack() },
                onStart = { names ->
                    // Cria lista de jogadores a partir dos nomes
                    val players = names.filter { it.isNotBlank() }.map { Player(it.trim()) }

                    // Inicializa GameState com jogadores + categoria
                    gameState = GameState(category = selectedCategory, players = players)

                    // Vai para Game e remove Setup da pilha
                    navController.navigate(Routes.Game) {
                        popUpTo(Routes.Setup) { inclusive = true }
                    }
                }
            )
        }

        // ------------------ GAME (PARTIDA) ------------------
        composable(Routes.Game) {
            GameScreen(
                state = gameState,
                onStateChange = { gameState = it },
                // Sair: volta para Home sem zerar a pilha inteira
                onExit = { navController.popBackStack(Routes.Home, false) },
                // Quando acabar: vai para tela GameOver
                onGameOver = { navController.navigate(Routes.GameOver) }
            )
        }

        // ------------------ GAME OVER ------------------
        composable(Routes.GameOver) {
            GameOverScreen(
                winner = gameState.winnerName ?: "Vencedor",
                category = gameState.category,
                onRestart = {
                    // Reinicia: zera estado e volta para Home
                    gameState = GameState(category = selectedCategory)
                    navController.popBackStack(Routes.Home, false)
                }
            )
        }
    }
}

/* ----------------------------- UI: FUNDO / CARD ----------------------------- */

// FunBackground: componente de fundo com gradiente (usado em todas as telas)
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

// GameCard: “card padrão” para organizar conteúdo das telas
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
                // bolinha de cor (accent) para dar identidade visual
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
    // Estados da tela (lembrados mesmo se recompor)
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
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Logo do app (precisa existir em res/drawable/logo.png)
                Spacer(Modifier.height(10.dp))
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo JogoStop",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(Modifier.height(10.dp))

                // Textos de boas-vindas
                Text(
                    text = "Bem-vindo(a)!",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Entre pra jogar um STOP mais divertido ✨",
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                // Card do login
                GameCard(
                    title = "Login",
                    subtitle = "Acesse sua conta para começar",
                    accent = FunCyan
                ) {
                    // Campo email (validação simples)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; erro = null },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Campo senha (com visualTransformation para esconder caracteres)
                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it; erro = null },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mostra mensagem de erro apenas se erro != null
                    AnimatedVisibility(erro != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(text = erro.orEmpty(), color = FunRed, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Botão entrar: valida campos e chama onLogin
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

                    // Navega para tela de cadastro
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
    // Estados do formulário de cadastro
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

                    // Erros de validação
                    AnimatedVisibility(erro != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(text = erro.orEmpty(), color = FunRed, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Validações do cadastro
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
    // Categorias disponíveis
    val categories = listOf("Animais", "Países", "Comidas", "Profissões", "Filmes", "Marcas", "Esportes")

    // Cor baseada na categoria
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
                    // Dropdown de categorias (Material3)
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

                    // Botões principais
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
fun InstructionsScreen(
    onBack: () -> Unit,
    onStartGame: () -> Unit
) {
    // Lista de “páginas” com instruções (cada item é uma página)
    val pages = listOf(
        "1) Preparação:\nColoque o celular no centro. Todos ao redor. Escolha uma categoria.",
        "2) Turnos:\nO primeiro turno é de quem digitou seu nome primeiro, e assim por diante.",
        "3) Palavras:\nA palavra vai aparecer aleatoriamente.",
        "4) Erros:\nSe não conseguir dizer uma palavra válida ou repetir uma já dita, perde e é eliminado.",
        "5) Consenso:\nSe alguém discordar, pause e votem. Quem perder a votação é eliminado.",
        "6) Vencedor:\nEliminados saem até restar 1. O último é o campeão!"
    )

    // Índice da página atual
    var index by rememberSaveable { mutableIntStateOf(0) }
    val isLast = index == pages.lastIndex

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

                    // Botões anterior / próximo
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { if (index > 0) index-- },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunBlue)
                        ) { Text("Anterior", fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = {
                                if (!isLast) index++
                                else onStartGame()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLast) FunGreen else FunPink)
                        ) {
                            Text(if (isLast) "Começar" else "Próximo", fontWeight = FontWeight.Bold)
                        }
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

    // Quantidade de jogadores (mín 2, máx 10)
    var qtd by rememberSaveable { mutableIntStateOf(3) }

    // Lista de nomes (tamanho acompanha qtd)
    var names by rememberSaveable { mutableStateOf(List(3) { "" }) }

    // Mensagem de erro (validação)
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    // syncList: ajusta a lista de nomes ao mudar qtd no Slider
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

                    // Slider controla qtd de jogadores
                    Slider(
                        value = qtd.toFloat(),
                        onValueChange = { syncList(it.toInt()) },
                        valueRange = 2f..10f,
                        steps = 7
                    )

                    Spacer(Modifier.height(10.dp))

                    // Cria campos de texto dinamicamente conforme qtd
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

                    // Começar: valida mínimo de 2 nomes preenchidos
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

/* ----------------------------- JOGO (TIMER + LETRAS SEM REPETIR + HISTÓRICO) ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onStateChange: (GameState) -> Unit,
    onExit: () -> Unit,
    onGameOver: () -> Unit
) {
    // Texto digitado pelo jogador no turno atual
    var word by rememberSaveable { mutableStateOf("") }

    // Mensagem de erro/eliminações
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // ------------------ TIMER (20 segundos) ------------------
    var timeLeft by rememberSaveable { mutableIntStateOf(20) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }

    // ------------------ CONSENSO / VOTAÇÃO ------------------
    var showConsensus by rememberSaveable { mutableStateOf(false) }
    var voteYes by rememberSaveable { mutableIntStateOf(0) }
    var voteNo by rememberSaveable { mutableIntStateOf(0) }

    // Atalhos para dados do estado atual
    val players = state.players
    val aliveCount = countAlive(players)
    val currentPlayer = players.getOrNull(state.currentIndex)
    val accent = categoryColor(state.category)

    // Cor visual do “disco” da letra (só UI)
    var letterColor by remember { mutableStateOf(FunPink) }

    // ===================================================================
    // FUNÇÕES DO JOGO (ELIMINAR, GIRAR LETRA, CONFIRMAR PALAVRA, ETC.)
    // ===================================================================

    // Atualiza lista de jogadores e checa se o jogo terminou (sobrou 1 vivo)
    fun updateAndCheckOver(newPlayers: List<Player>, nextIndexFrom: Int) {
        val winner = computeWinner(newPlayers)
        if (winner != null) {
            // Marca como finalizado e salva o vencedor
            onStateChange(state.copy(players = newPlayers, isOver = true, winnerName = winner))
            onGameOver()
            return
        }

        // Se ainda não acabou: calcula próximo jogador vivo e reseta turno
        val nextIdx = nextActiveIndex(newPlayers, nextIndexFrom) ?: 0
        onStateChange(
            state.copy(
                players = newPlayers,
                currentIndex = nextIdx,
                currentLetter = null,
                lastWord = null
            )
        )

        // Reseta estado local do turno
        timerRunning = false
        timeLeft = 20
        word = ""
        error = null
    }

    // Elimina o jogador atual e passa para o próximo
    fun eliminateCurrent(reason: String) {
        timerRunning = false

        val newPlayers = players.mapIndexed { i, p ->
            if (i == state.currentIndex) p.copy(eliminated = true) else p
        }

        // Mensagem explicando a eliminação (aparece na UI)
        error = "Eliminado: ${currentPlayer?.name ?: ""} ($reason)"

        updateAndCheckOver(newPlayers, state.currentIndex)
    }

    // ------------------ GIRAR LETRA (SEM REPETIR) ------------------
    fun spinLetter() {
        // Pega apenas as letras que ainda não foram usadas
        val remaining = LettersPT.filter { it !in state.usedLetters }

        // Se acabou tudo, avisa
        if (remaining.isEmpty()) {
            error = "Acabaram as letras disponíveis (sem repetição)."
            return
        }

        // Sorteia uma letra aleatória
        val letter = remaining.random(Random(System.currentTimeMillis()))

        // Cor aleatória do disco (efeito visual)
        letterColor = FunPalette.random()

        // Atualiza estado do jogo:
        // - define currentLetter
        // - adiciona a letra em usedLetters (para não repetir)
        onStateChange(
            state.copy(
                currentLetter = letter,
                lastWord = null,
                usedLetters = state.usedLetters + letter
            )
        )

        // Limpa inputs do turno
        word = ""
        error = null
    }

    // ------------------ CONFIRMAR PALAVRA ------------------
    fun submitWord() {
        val letter = state.currentLetter
        val player = currentPlayer ?: return

        // Precisa ter letra sorteada
        if (letter == null) {
            error = "Gire a letra primeiro."
            return
        }

        // Se tempo acabou, elimina
        if (timeLeft <= 0) {
            eliminateCurrent("tempo esgotado")
            return
        }

        val w = word.trim()

        // Palavra vazia -> elimina
        if (w.isBlank()) {
            eliminateCurrent("não falou palavra")
            return
        }

        // Checa se começa com a letra sorteada
        val first = w.first().uppercaseChar().toString()
        if (first != letter) {
            eliminateCurrent("não começou com '$letter'")
            return
        }

        // Normaliza para impedir repetição (ex: “Foca” e “foca” = mesma)
        val normalized = w.lowercase()
        if (state.usedWords.contains(normalized)) {
            eliminateCurrent("repetiu palavra")
            return
        }

        // ✅ Se chegou aqui: palavra é ACEITA
        timerRunning = false

        // Atualiza set de palavras usadas (para não repetir)
        val newUsed = state.usedWords + normalized

        // Salva no histórico permanente (fica até acabar o jogo)
        val newAccepted = state.acceptedWords + WordEntry(
            playerName = player.name,
            letter = letter,
            word = w
        )

        // Passa para o próximo jogador vivo
        val nextIdx = nextActiveIndex(players, state.currentIndex) ?: state.currentIndex

        // Atualiza o estado global do jogo
        onStateChange(
            state.copy(
                usedWords = newUsed,
                acceptedWords = newAccepted,
                lastWord = w,
                currentIndex = nextIdx,
                currentLetter = null // limpa letra para obrigar girar de novo
            )
        )

        // Limpa input do turno
        word = ""
        error = null
    }

    // ===================================================================
    // TIMER AUTOMÁTICO COM LaunchedEffect
    // - dispara quando currentLetter muda (quando gira letra)
    // - também reage quando muda o jogador (currentIndex)
    // ===================================================================
    LaunchedEffect(state.currentLetter, state.currentIndex) {
        // Só roda timer se existe letra e o jogo não acabou
        if (state.currentLetter != null && !state.isOver) {
            timeLeft = 20
            timerRunning = true

            // Loop: a cada 1s diminui 1 do tempo
            while (timerRunning && timeLeft > 0) {
                delay(1000)
                timeLeft--
            }

            // Se chegou a 0 e ainda estava rodando, elimina por tempo
            if (timerRunning && timeLeft == 0) {
                eliminateCurrent("tempo esgotado")
                timerRunning = false
            }
        } else {
            // Se não tem letra (ainda não girou), reseta timer
            timerRunning = false
            timeLeft = 20
        }
    }

    // ===================================================================
    // UI DA TELA DO JOGO
    // ===================================================================
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
                    // “Pausar” abre o modal de votação/consenso
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

                // Card principal do turno
                GameCard(
                    title = "Vez de: ${currentPlayer?.name ?: "-"}",
                    subtitle = "Vivos: $aliveCount",
                    accent = accent
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Caixa que mostra letra sorteada e timer
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF8FAFC)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                // Disco com borda colorida e letra no meio
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

                                // Cor do texto do timer muda quando está perto de acabar
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

                        // Botão para girar a letra
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

                    // Campo para digitar palavra
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it; error = null },
                        label = { Text("Palavra da categoria") },
                        placeholder = { Text("Ex: Foca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Botões de ação do turno
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                        // “Perdi” elimina manualmente (desistiu)
                        Button(
                            onClick = { eliminateCurrent("desistiu") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunOrange)
                        ) { Text("Perdi", fontWeight = FontWeight.Black) }

                        // Confirmar valida e aceita/elimina
                        Button(
                            onClick = { submitWord() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FunGreen)
                        ) { Text("Confirmar", fontWeight = FontWeight.Black) }
                    }

                    // Mostra última palavra aceita
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

                    // Mostra erros/eliminação
                    AnimatedVisibility(error != null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(error.orEmpty(), color = FunRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Card do histórico de palavras aceitas (permanece até o final)
                GameCard(
                    title = "Palavras aceitas",
                    subtitle = "Ficam aqui até o fim do jogo ✅",
                    accent = FunPurple
                ) {
                    if (state.acceptedWords.isEmpty()) {
                        Text(
                            "Ainda não tem nenhuma palavra aceita.",
                            color = Color(0xFF6B7280)
                        )
                    } else {
                        // Mostra do mais recente para o mais antigo
                        state.acceptedWords.asReversed().forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "(${entry.letter})",
                                    fontWeight = FontWeight.Black,
                                    color = FunPurple
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.word,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    entry.playerName,
                                    color = Color(0xFF6B7280),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Card de status dos jogadores (eliminado / no jogo)
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

        // ===================================================================
        // MODAL DE CONSENSO / VOTAÇÃO
        // Usado quando alguém discorda da palavra (decidem se elimina ou mantém)
        // ===================================================================
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

                            // Regra: se SIM > NÃO -> elimina
                            if (voteYes > voteNo) {
                                eliminateCurrent("perdeu a votação")
                            } else {
                                // Caso contrário, mantém e passa o turno
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
                    // Mostra vencedor
                    Text(
                        "🏆 $winner",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    // Reiniciar: volta ao início (Home) via callback
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
// Preview para testar a tela no Android Studio sem rodar o app
@Preview(showBackground = true)
@Composable
fun PreviewLogin() {
    JogoStopTheme {
        LoginScreen(onLogin = { _, _ -> }, onGoToRegister = {})
    }
}
