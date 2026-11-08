# Bert Health Live

Persoonlijk webdashboard voor gegevens van Galaxy Watch 8 via Samsung Health en Health Connect.

## Status
Dit is de eerste dashboardbasis. GitHub Pages kan niet zelf Health Connect uitlezen. Daarvoor is een Android-companion-app nodig met Health Connect-toestemming. Een veilige synchronisatieservice/API is ook nodig voor automatische updates op meerdere apparaten.

## Publiceren
1. Open **Settings → Pages** in deze repository.
2. Kies **Deploy from a branch**.
3. Selecteer branch **main**, map **/(root)** en klik **Save**.
4. De website wordt daarna beschikbaar op https://meringa100-cmyk.github.io/BertHealth/

## Huidige mogelijkheden
- Dashboardweergave voor hartslag, stappen, slaap, SpO₂, HRV en stress.
- JSON-import in de browser.
- Geïmporteerde gegevens worden alleen lokaal in de browser bewaard; ze worden niet naar deze openbare repository verstuurd.

## Volgende technische stap
Bouw de Android-brug met Health Connect SDK. Vraag alleen de benodigde leesrechten aan, lees records op het toestel en stuur gegevens pas naar een backend nadat authenticatie, versleuteling en bewaartermijnen zijn ingericht. Plaats nooit gezondheidsgegevens, tokens of API-sleutels in deze publieke repository.

## Let op
Horlogemetingen zijn indicatief en geen medische diagnose.