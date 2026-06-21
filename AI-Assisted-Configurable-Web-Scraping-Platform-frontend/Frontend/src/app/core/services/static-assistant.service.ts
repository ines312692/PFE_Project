import {Injectable} from '@angular/core';

export interface StaticAnswer {
  title: string;
  body: string;
  matched: boolean;
}

interface KnowledgeEntry {
  id: string;
  keywords: string[];
  title: string;
  body: string;
}

@Injectable({providedIn: 'root'})
export class StaticAssistantService {

  private readonly knowledge: KnowledgeEntry[] = [
    {
      id: 'first-job',
      keywords: ['first', 'create', 'créer', 'premier', 'job', 'start', 'commencer', 'step'],
      title: 'Créer votre premier job de scraping',
      body: [
        '1. **URL cible** — saisissez l’URL de la page à extraire.',
        '2. **Schéma d’extraction** — définissez les champs (sélecteurs CSS/XPath, mode IA, ou regex).',
        '3. **Paramètres de crawl** — profondeur, nombre de pages, filtres include/exclude.',
        '4. **Mode test (dry-run)** — validez sur un échantillon avant le lancement complet.',
        '5. **Planification** — manuel, cron, ou déclenché par webhook.',
        '6. **Lancer** — suivez l’exécution depuis le dashboard.',
      ].join('\n'),
    },
    {
      id: 'llm-extraction',
      keywords: ['llm', 'ia', 'ai', 'semantic', 'sémantique', 'extraction', 'natural', 'langage naturel'],
      title: 'Extraction par IA / LLM',
      body: [
        'L’extraction LLM vous laisse **décrire en langage naturel** ce que vous voulez (ex : « extraire tous les prix et noms de produits »).',
        '',
        'Le modèle analyse la page, identifie les éléments pertinents et retourne du **JSON structuré** — même sur des pages non structurées ou au markup variable.',
        '',
        '**Quand l’utiliser plutôt que CSS/XPath :**',
        '- Layout instable ou changeant souvent',
        '- Pages hétérogènes (markup variable)',
        '- Pas envie d’écrire de sélecteurs',
        '',
        'Pour des pages stables et un contrôle précis, les **sélecteurs CSS/XPath** restent plus rapides et déterministes. Vous pouvez combiner les deux modes dans un même job.',
      ].join('\n'),
    },
    {
      id: 'ecommerce',
      keywords: ['e-commerce', 'ecommerce', 'commerce', 'product', 'produit', 'prix', 'price', 'stock', 'reviews', 'avis'],
      title: 'Scraper un site e-commerce',
      body: [
        'Utilisez le **template e-commerce** prédéfini, qui couvre : prix, nom du produit, disponibilité/stock, et avis clients.',
        '',
        '**Conseils :**',
        '- Activez le **rendu JavaScript** (Playwright) si les prix sont chargés dynamiquement.',
        '- Gérez la **pagination** (numérotée, scroll infini, bouton « charger plus »).',
        '- Activez le **nettoyage des prix** pour normaliser séparateurs, décimales et devises.',
        '- Planifiez une exécution récurrente pour suivre les variations de prix.',
      ].join('\n'),
    },
    {
      id: 'anti-bot',
      keywords: ['cloudflare', 'anti-bot', 'antibot', 'bypass', 'contourner', 'datadome', 'akamai', 'captcha', 'stealth', 'protection'],
      title: 'Contourner Cloudflare et les protections anti-bot',
      body: [
        'ElyaScrape combine plusieurs mécanismes :',
        '',
        '- **Stealth mode** : masquage de `navigator.webdriver`, spoofing canvas/WebGL.',
        '- **Rotation de fingerprint** : User-Agent, Accept-Language, fingerprint TLS (JA3/JA4).',
        '- **Proxies résidentiels** avec rotation et géolocalisation.',
        '- **Délais aléatoires** et auto-throttle adaptatif.',
        '- **Réutilisation du cookie `cf_clearance`** et des sessions.',
        '- **CAPTCHA solving** via plugins extensibles.',
        '',
        'Lancez d’abord l’**analyse du site cible** : elle détecte le type de protection (Cloudflare IUAM/Turnstile/WAF, DataDome, Akamai) et recommande la stratégie adaptée.',
      ].join('\n'),
    },
    {
      id: 'proxies',
      keywords: ['proxy', 'proxies', 'proxie', 'residential', 'résidentiel', 'datacenter', 'mobile', 'rotation', 'geo', 'géo'],
      title: 'Configurer les proxies',
      body: [
        '**Types disponibles :** datacenter, résidentiel, mobile.',
        '',
        '**Configuration :**',
        '- Définissez un **pool** de proxies avec rotation automatique.',
        '- **Géo-targeting** : sélectionnez le pays/région pour le contenu géo-restreint.',
        '- Le **circuit breaker** désactive temporairement un proxy au taux d’erreur élevé.',
        '- Surveillez la consommation des proxies dans le dashboard de monitoring.',
      ].join('\n'),
    },
    {
      id: 'google-maps',
      keywords: ['google maps', 'maps', 'business', 'listing', 'entreprise', 'adresse', 'address'],
      title: 'Extraire des données de Google Maps',
      body: [
        'Pour les listings de type Google Maps, ciblez : **nom de l’entreprise, adresse, téléphone, note/avis**.',
        '',
        '- Activez le **rendu JavaScript** (contenu chargé dynamiquement).',
        '- Utilisez l’**extraction IA** pour des cartes au markup variable.',
        '- Gérez le **scroll infini** pour charger l’ensemble des résultats.',
        '- Activez la **conformité RGPD** : anonymisation des données personnelles si nécessaire.',
      ].join('\n'),
    },
    {
      id: 'integrations',
      keywords: ['n8n', 'zapier', 'webhook', 'integration', 'intégration', 'airtable', 'notion', 'sheets', 'automation'],
      title: 'Intégrer avec n8n, Zapier et autres',
      body: [
        '**Export simplifié** en un clic vers : Google Sheets, Excel, CSV, JSON.',
        '',
        '**Automatisation :**',
        '- **Webhooks** : déclenchez un flux externe à la fin d’un job.',
        '- **Crawling événementiel** : déclenchez un crawl depuis un événement externe.',
        '- Intégration directe avec **Zapier, Airtable, Notion**, et n8n via webhooks REST.',
      ].join('\n'),
    },
    {
      id: 'etl',
      keywords: ['clean', 'nettoyer', 'normalize', 'normaliser', 'etl', 'pipeline', 'dedup', 'doublon', 'validation', 'enrich'],
      title: 'Nettoyage, normalisation et pipeline ETL',
      body: [
        'Le pipeline ETL d’ElyaScrape enchaîne :',
        '',
        '1. **Nettoyage** : suppression des balises HTML, espaces, caractères/encodages incorrects.',
        '2. **Déduplication** : par clé configurable ou déduplication sémantique IA.',
        '3. **Normalisation** : dates (ISO 8601), devises, unités, téléphones (E.164), adresses.',
        '4. **Validation** : règles par champ (Pydantic/Cerberus) + rapport de qualité.',
        '5. **Enrichissement IA** : KPIs, sentiment, NER, catégorisation, résumés.',
        '6. **Export** : PostgreSQL, MongoDB, CSV/JSON/Parquet/Excel, APIs REST, webhooks.',
        '',
        'Chaque extraction reçoit un **score de qualité** (complétude, exactitude, cohérence, fraîcheur).',
      ].join('\n'),
    },
  ];

  private readonly fallback: StaticAnswer = {
    title: 'Je n’ai pas de réponse précise à cela',
    body: [
      'Je peux vous aider sur les sujets suivants :',
      '',
      '- Créer un job de scraping',
      '- Extraction par sélecteurs CSS/XPath ou par IA/LLM',
      '- Contourner Cloudflare et les protections anti-bot',
      '- Configurer les proxies (datacenter / résidentiel / mobile)',
      '- Nettoyer et normaliser les données (pipeline ETL)',
      '- Intégrer avec n8n, Zapier, Airtable, Notion',
      '',
      'Reformulez votre question avec un de ces mots-clés.',
    ].join('\n'),
    matched: false,
  };

  ask(prompt: string): StaticAnswer {
    const text = this.normalize(prompt);

    let best: { entry: KnowledgeEntry; score: number } | null = null;

    for (const entry of this.knowledge) {
      let score = 0;
      for (const kw of entry.keywords) {
        if (text.includes(this.normalize(kw))) score++;
      }
      if (score > 0 && (!best || score > best.score)) {
        best = {entry, score};
      }
    }

    if (!best) return {...this.fallback};

    return {
      title: best.entry.title,
      body: best.entry.body,
      matched: true,
    };
  }

  private normalize(s: string): string {
    return s
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }
}
