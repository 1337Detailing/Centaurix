package fr.centaurix.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.Locale

data class CentaurixPalette(
    val primary:Color, val background:Color, val surface:Color, val raised:Color,
    val border:Color, val text:Color, val muted:Color, val success:Color, val error:Color
)

object CentaurixUi {
    private var prefs:SharedPreferences?=null
    var language by mutableStateOf("system")
        private set
    var theme by mutableStateOf("system")
        private set
    var accent by mutableStateOf("gold")
        private set
    var customHex by mutableStateOf("#D8B65A")
        private set
    var systemDark by mutableStateOf(true)
    var systemAccentColor by mutableStateOf(Color(0xFFA6B987))
    var dynamicColors by mutableStateOf(false)
        private set
    var surfaceIntensity by mutableStateOf("subtle")
        private set

    fun initialize(context:Context) {
        if(prefs!=null)return
        prefs=context.getSharedPreferences("centaurix-ui-v1",Context.MODE_PRIVATE)
        language=prefs?.getString("language","system") ?: "system"
        theme=prefs?.getString("theme","system") ?: "system"
        accent=prefs?.getString("accent","gold") ?: "gold"
        customHex=prefs?.getString("customHex","#D8B65A") ?: "#D8B65A"
        dynamicColors=prefs?.getBoolean("dynamicColors",false) ?: false
        surfaceIntensity=prefs?.getString("surfaceIntensity","subtle") ?: "subtle"
    }
    fun chooseLanguage(value:String) {
        require(value in listOf("system","fr","en","de","ru","pl"))
        language=value; prefs?.edit()?.putString("language",value)?.apply()
    }
    fun chooseTheme(value:String) {
        require(value in listOf("system","dark","light","amoled"))
        theme=value; prefs?.edit()?.putString("theme",value)?.apply()
    }
    fun chooseAccent(value:String) {
        require(value in listOf("gold","pistachio","blue","purple","teal","coral","green","custom"))
        accent=value; dynamicColors=false
        prefs?.edit()?.putString("accent",value)?.putBoolean("dynamicColors",false)?.apply()
    }
    fun chooseSurfaceIntensity(value:String) {
        require(value in listOf("subtle","normal","strong"))
        surfaceIntensity=value; prefs?.edit()?.putString("surfaceIntensity",value)?.apply()
    }
    fun chooseDynamicColors(value:Boolean) {
        dynamicColors=value; prefs?.edit()?.putBoolean("dynamicColors",value)?.apply()
    }
    fun setCustomHex(value:String):Boolean {
        val h=("#"+value.trim().removePrefix("#")).uppercase(Locale.ROOT)
        if(!Regex("^#[0-9A-F]{6}$").matches(h))return false
        customHex=h
        prefs?.edit()?.putString("customHex",h)?.apply()
        chooseAccent("custom")
        return true
    }
    private fun code()=if(language=="system")Locale.getDefault().language else language
    fun locale():Locale=when(code()) {
        "fr"->Locale.FRENCH; "de"->Locale.GERMAN;"ru"->Locale("ru");"pl"->Locale("pl");else->Locale.ENGLISH
    }
    private val strings=mapOf(
        "Accueil" to listOf("Home", "Start", "Главная", "Start"),
        "Profils" to listOf("Profiles", "Profile", "Профили", "Profile"),
        "Thèmes" to listOf("Themes", "Designs", "Темы", "Motywy"),
        "Bouffées" to listOf("Puffs", "Züge", "Затяжки", "Zaciągnięcia"),
        "Plus" to listOf("More", "Mehr", "Ещё", "Więcej"),
        "Connecter" to listOf("Connect", "Verbinden", "Подключить", "Połącz"),
        "Connectée" to listOf("Connected", "Verbunden", "Подключено", "Połączono"),
        "Réglages" to listOf("Settings", "Einstellungen", "Настройки", "Ustawienia"),
        "Langue" to listOf("Language", "Sprache", "Язык", "Język"),
        "Automatique" to listOf("System", "System", "Система", "System"),
        "Apparence" to listOf("Appearance", "Darstellung", "Оформление", "Wygląd"),
        "Système" to listOf("System", "System", "Система", "System"),
        "Sombre" to listOf("Dark", "Dunkel", "Тёмная", "Ciemny"),
        "Clair" to listOf("Light", "Hell", "Светлая", "Jasny"),
        "Couleur d'accent" to listOf("Accent color", "Akzentfarbe", "Цвет акцента", "Kolor akcentu"),
        "Thème de l'application" to listOf("App theme", "App-Design", "Тема приложения", "Motyw aplikacji"),
        "Intensité des surfaces" to listOf("Surface intensity", "Oberflächenintensität", "Насыщенность поверхностей", "Intensywność powierzchni"),
        "Teinte légère" to listOf("Subtle tint", "Leichte Tönung", "Лёгкий оттенок", "Lekki odcień"),
        "Normale" to listOf("Normal", "Normal", "Обычный", "Normalna"),
        "Prononcée" to listOf("Strong", "Kräftig", "Выраженный", "Mocna"),
        "Pistache" to listOf("Pistachio", "Pistazie", "Фисташковый", "Pistacjowy"),
        "Material You dynamique" to listOf("Dynamic Material You", "Dynamisches Material You", "Динамический Material You", "Dynamiczny Material You"),
        "Statistiques" to listOf("Statistics", "Statistik", "Статистика", "Statystyki"),
        "Tableau de bord" to listOf("Dashboard", "Übersicht", "Обзор", "Panel"),
        "Profils" to listOf("Profiles", "Profile", "Профили", "Profile"),
        "Courbe VPC" to listOf("VPC curve", "VPC-Kurve", "Кривая VPC", "Krzywa VPC"),
        "Thèmes de la box" to listOf("Device themes", "Gerätethemen", "Темы устройства", "Motywy urządzenia"),
        "Aujourd'hui" to listOf("Today", "Heute", "Сегодня", "Dzisiaj"),
        "AUJOURD'HUI" to listOf("TODAY", "HEUTE", "СЕГОДНЯ", "DZISIAJ"),
        "7 jours" to listOf("7 days", "7 Tage", "7 дней", "7 dni"),
        "30 jours" to listOf("30 days", "30 Tage", "30 дней", "30 dni"),
        "Cette semaine" to listOf("This week", "Diese Woche", "Эта неделя", "Ten tydzień"),
        "Historique quotidien" to listOf("Daily history", "Tagesverlauf", "История по дням", "Historia dzienna"),
        "Bouffées aujourd'hui" to listOf("Puffs today", "Züge heute", "Затяжки сегодня", "Dziś zaciągnięć"),
        "Aucune activité" to listOf("No activity", "Keine Aktivität", "Нет активности", "Brak aktywności"),
        "Mode" to listOf("Mode", "Modus", "Режим", "Tryb"),
        "Puissance" to listOf("Power", "Leistung", "Мощность", "Moc"),
        "Température" to listOf("Temperature", "Temperatur", "Температура", "Temperatura"),
        "Tension" to listOf("Voltage", "Spannung", "Напряжение", "Napięcie"),
        "Réponse Power" to listOf("Power response", "Power-Reaktion", "Режим Power", "Reakcja Power"),
        "Relire le profil" to listOf("Reload profile", "Profil neu laden", "Обновить профиль", "Odśwież profil"),
        "Réinitialiser ce profil" to listOf("Reset profile", "Profil zurücksetzen", "Сбросить профиль", "Resetuj profil"),
        "Confirmer" to listOf("Confirm", "Bestätigen", "Подтвердить", "Potwierdź"),
        "Annuler" to listOf("Cancel", "Abbrechen", "Отмена", "Anuluj"),
        "Enregistrer" to listOf("Save", "Speichern", "Сохранить", "Zapisz"),
        "Enregistrer la courbe VPC" to listOf("Save VPC curve", "VPC-Kurve speichern", "Сохранить кривую VPC", "Zapisz krzywą VPC"),
        "Envoyer le profil sur la box" to listOf("Save profile", "Profil speichern", "Сохранить профиль", "Zapisz profil"),
        "Courbe de puissance" to listOf("Power curve", "Leistungskurve", "Кривая мощности", "Krzywa mocy"),
        "Synchroniser la box" to listOf("Sync device", "Gerät synchronisieren", "Синхронизировать", "Synchronizuj"),
        "Reconnecter" to listOf("Reconnect", "Neu verbinden", "Переподключить", "Połącz ponownie"),
        "Déconnecter" to listOf("Disconnect", "Trennen", "Отключить", "Rozłącz"),
        "Effacer l'historique local" to listOf("Clear history", "Verlauf löschen", "Очистить историю", "Wyczyść historię"),
        "Effacer les statistiques locales" to listOf("Clear statistics", "Statistik löschen", "Удалить статистику", "Wyczyść statystyki"),
        "Partager le diagnostic" to listOf("Share diagnostics", "Diagnose teilen", "Отправить диагностику", "Udostępnij diagnostykę"),
        "Illustration" to listOf("Illustrative preview", "Beispielbild", "Пример оформления", "Podgląd poglądowy"),
        "Mon propre fond d'écran" to listOf("Custom wallpaper", "Eigenes Hintergrundbild", "Свои обои", "Własna tapeta"),
        "Choisir et transférer une image" to listOf("Upload image", "Bild hochladen", "Загрузить изображение", "Prześlij obraz"),
        "Autonomie" to listOf("Battery", "Akku", "Батарея", "Bateria"),
        "En un coup d'œil" to listOf("Overview", "Übersicht", "Обзор", "Przegląd"),
        "Actualiser les données" to listOf("Refresh", "Aktualisieren", "Обновить", "Odśwież"),
        "Puissance & modes" to listOf("Power & modes", "Leistung & Modi", "Мощность и режимы", "Moc i tryby"),
        "Personnalisation" to listOf("Customization", "Anpassung", "Настройка", "Personalizacja"),
        "Journal & confidentialité" to listOf("Diagnostics", "Diagnose", "Диагностика", "Diagnostyka"),
        "Historique local" to listOf("Local history", "Lokaler Verlauf", "Локальная история", "Historia lokalna")
    )
    private val additionalStrings=mapOf(
        "Permissions nécessaires" to listOf("Permissions required", "Berechtigungen erforderlich", "Нужны разрешения", "Wymagane uprawnienia"),
        "Compris" to listOf("OK", "OK", "Понятно", "Rozumiem"),
        "Ta BT200 n'est pas connectée" to listOf("BT200 disconnected", "BT200 nicht verbunden", "BT200 не подключён", "BT200 rozłączony"),
        "Active le Bluetooth dans le menu de la box, puis confirme avec le bouton central." to listOf("Enable Bluetooth on your BT200.", "Bluetooth am BT200 aktivieren.", "Включите Bluetooth на BT200.", "Włącz Bluetooth w BT200."),
        "MODE" to listOf("MODE", "MODUS", "РЕЖИМ", "TRYB"),
        "Profil" to listOf("Profile", "Profil", "Профиль", "Profil"),
        "Cette courbe répartit la puissance sur 8 étapes." to listOf("8 power points.", "8 Leistungsstufen.", "8 точек мощности.", "8 punktów mocy."),
        "Enregistrer sur la BT200 ?" to listOf("Save to BT200?", "Auf BT200 speichern?", "Сохранить на BT200?", "Zapisać na BT200?"),
        "Cette opération effacera ses réglages enregistrés sur la BT200." to listOf("Device profile settings will be reset.", "Geräteprofil wird zurückgesetzt.", "Настройки профиля будут сброшены.", "Profil urządzenia zostanie zresetowany."),
        "Réinitialiser" to listOf("Reset", "Zurücksetzen", "Сбросить", "Resetuj"),
        "Activer la courbe VPC ?" to listOf("Save VPC curve?", "VPC-Kurve speichern?", "Сохранить кривую VPC?", "Zapisać krzywą VPC?"),
        "Collections officielles" to listOf("Device themes", "Gerätethemen", "Темы устройства", "Motywy urządzenia"),
        "Diagnostics : lectures du compteur" to listOf("Counter readings", "Zählerstände", "Показания счётчика", "Odczyty licznika"),
        "Valeurs cumulatives brutes (pas les puffs de chaque jour). Voir Puffs pour le suivi quotidien." to listOf("Cumulative device counter.", "Kumulativer Gerätezähler.", "Общий счётчик устройства.", "Całkowity licznik urządzenia."),
        "Effacer l'historique ?" to listOf("Clear history?", "Verlauf löschen?", "Очистить историю?", "Wyczyścić historię?"),
        "Les données enregistrées sur le téléphone seront supprimées, sans modifier la BT200." to listOf("Delete local data only?", "Nur lokale Daten löschen?", "Удалить локальные данные?", "Usunąć dane lokalne?"),
        "Effacer" to listOf("Clear", "Löschen", "Очистить", "Wyczyść"),
        "bouffées observées" to listOf("puffs recorded", "Züge erfasst", "Учтено затяжек", "Zaciągnięć zapisano"),
        "Aucune lecture enregistrée ce mois-là." to listOf("No readings for this month.", "Keine Daten für diesen Monat.", "Нет показаний за месяц.", "Brak odczytów w tym miesiącu."),
        "Supprimer le suivi ?" to listOf("Delete tracking history?", "Verlauf löschen?", "Удалить историю?", "Usunąć historię?"),
        "Les relevés journaliers locaux seront supprimés. Le compteur de la BT200 ne sera pas modifié." to listOf("Delete local history only?", "Nur lokale Statistik löschen?", "Удалить локальную историю?", "Usunąć lokalną historię?"),
        "Supprimer" to listOf("Delete", "Löschen", "Удалить", "Usuń"),
        "Associer la BT200" to listOf("Pair BT200", "BT200 koppeln", "Подключить BT200", "Sparuj BT200"),
        "Sur la box : Bluetooth ON + validation avec le bouton central." to listOf("Enable Bluetooth on BT200.", "Bluetooth am BT200 aktivieren.", "Включите Bluetooth на BT200.", "Włącz Bluetooth w BT200."),
        "Connecter la Centaurus BT200" to listOf("Connect BT200", "BT200 verbinden", "Подключить BT200", "Połącz BT200"),
        "Temps brut" to listOf("Usage time", "Nutzungsdauer", "Время использования", "Czas użytkowania"),
        "Profil actif" to listOf("Active profile", "Aktives Profil", "Активный профиль", "Aktywny profil"),
        "Rechercher la BT200" to listOf("Find BT200", "BT200 suchen", "Найти BT200", "Znajdź BT200"),
        "Choisir et transférer une image" to listOf("Upload wallpaper", "Hintergrundbild hochladen", "Загрузить обои", "Prześlij tapetę"),
        "Relancer le scan" to listOf("Rescan", "Erneut suchen", "Повторить поиск", "Skanuj ponownie"),
        "Box (total)" to listOf("Device total", "Gerätegesamtzahl", "Всего на устройстве", "Licznik urządzenia"),
        "7 jours" to listOf("7 days", "7 Tage", "7 дней", "7 dni"),
        "30 jours" to listOf("30 days", "30 Tage", "30 дней", "30 dni"),
        "Mettre à jour" to listOf("Refresh", "Aktualisieren", "Обновить", "Odśwież"),
        "Déconnectée" to listOf("Disconnected", "Getrennt", "Отключено", "Rozłączono"),
        "Réglage accepté. Vérification sur la box…" to listOf("Checking saved profile…", "Profil wird geprüft…", "Проверка профиля…", "Sprawdzanie profilu…"),
        "Synchronisation en cours…" to listOf("Syncing…", "Synchronisiere…", "Синхронизация…", "Synchronizowanie…"),
        "Envoi du profil • attente de confirmation" to listOf("Saving profile…", "Profil wird gespeichert…", "Сохранение профиля…", "Zapisywanie profilu…"),
        "Courbe VPC enregistrée et relue ✅" to listOf("VPC curve saved ✅", "VPC-Kurve gespeichert ✅", "Кривая VPC сохранена ✅", "Krzywa VPC zapisana ✅")
    )
    private val statusStrings=mapOf(
        "BT200 • EN LIGNE" to listOf("BT200 • ONLINE", "BT200 • ONLINE", "BT200 • VERBUNDEN", "BT200 • POŁĄCZONO"),
        "BT200 • HORS LIGNE" to listOf("BT200 • OFFLINE", "BT200 • OFFLINE", "BT200 • ОФЛАЙН", "BT200 • OFFLINE"),
        "Synchronisé depuis la box" to listOf("Device synchronized", "Vom Gerät synchronisiert", "Синхронизировано", "Zsynchronizowano"),
        "Lecture en attente…" to listOf("Loading profile…", "Profil wird geladen…", "Загрузка профиля…", "Ładowanie profilu…"),
        "Pas de mesure" to listOf("No reading", "Keine Messung", "Нет измерений", "Brak pomiarów"),
        "Recherche en cours…" to listOf("Scanning…", "Suche läuft…", "Поиск…", "Skanowanie…"),
        "Recherche terminée" to listOf("Scan complete", "Suche abgeschlossen", "Поиск завершён", "Skanowanie zakończone")
    )
    fun text(key:String):String {
        val code=code()
        if(code=="fr")return key
        val position=when(code){"de"->1;"ru"->2;"pl"->3;else->0}
        val known=strings[key] ?: additionalStrings[key] ?: statusStrings[key]
        if(known!=null)return known[position]
        val count=Regex("""^(\d+) bouffées$""").matchEntire(key)
        if(count!=null)return count.groupValues[1]+" "+(strings["Bouffées"]?.get(position) ?: "puffs")
        val profile=Regex("""^Profil (\d+)$""").matchEntire(key)
        if(profile!=null)return (additionalStrings["Profil"]?.get(position) ?: "Profile")+" "+profile.groupValues[1]
        return key
    }
    val dark:Boolean get()=when(theme) {"light"->false;"dark","amoled"->true;else->systemDark}
    fun palette():CentaurixPalette {
        val color=when(accent) {
            "pistachio"->0xFFA6B987L
            "blue"->0xFF5796E5L;"purple"->0xFFAE8CE8L;"teal"->0xFF51B5B7L
            "coral"->0xFFE68C76L;"green"->0xFF7BC9A2L
            "custom"->runCatching { android.graphics.Color.parseColor(customHex).toLong() and 0xFFFFFFFFL }.getOrDefault(0xFFD8B65AL)
            else->0xFFD8B65AL
        }
        val isDark=dark
        val amoled=theme=="amoled"
        val surface=when {
            !isDark->when(surfaceIntensity) {
                "normal"->Color(0xFFF6F4F0); "strong"->Color(0xFFEDE7DA); else->Color.White
            }
            amoled->when(surfaceIntensity) {
                "normal"->Color(0xFF171717); "strong"->Color(0xFF242424); else->Color(0xFF101010)
            }
            else->when(surfaceIntensity) {
                "normal"->Color(0xFF24252A); "strong"->Color(0xFF303038); else->Color(0xFF1F2026)
            }
        }
        return CentaurixPalette(
            primary=if(dynamicColors && android.os.Build.VERSION.SDK_INT>=31)systemAccentColor else Color(color.toInt()),
            background=if(!isDark)Color(0xFFFAF9F6) else if(amoled)Color.Black else Color(0xFF0D0E13),
            surface=surface,
            raised=if(!isDark)Color(0xFFEDEAE5) else if(amoled)Color(0xFF292929) else Color(0xFF32343A),
            border=if(!isDark)Color(0xFFDCD8CE) else Color(0xFF373941),
            text=if(!isDark)Color(0xFF1D1F24) else Color(0xFFF8F6FC),
            muted=if(!isDark)Color(0xFF666570) else Color(0xFFA9A7B3),
            success=if(!isDark)Color(0xFF317C55) else Color(0xFF80D5A4),
            error=if(!isDark)Color(0xFFAD413B) else Color(0xFFF29B91)
        )
    }
}
