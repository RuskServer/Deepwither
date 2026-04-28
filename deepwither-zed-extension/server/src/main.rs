use tower_lsp::jsonrpc::Result;
use tower_lsp::lsp_types::*;
use tower_lsp::{Client, LanguageServer, LspService, Server};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use dashmap::DashMap;
use walkdir::WalkDir;
use serde_yaml::Value;
use std::path::Path;
use std::sync::Arc;

#[allow(non_camel_case_types)]
#[derive(Debug, Deserialize, Serialize, Clone, Copy, PartialEq, Eq, Hash)]
enum StatType {
    ATTACK_DAMAGE,
    ATTACK_SPEED,
    PROJECTILE_DAMAGE,
    PROJECTILE_SPEED,
    MAGIC_DAMAGE,
    MAGIC_AOE_BONUS,
    MAGIC_BURST_BONUS,
    CRIT_CHANCE,
    CRIT_DAMAGE,
    MAX_HEALTH,
    MAX_MANA,
    DEFENSE,
    MAGIC_RESIST,
    MOVE_SPEED,
    LIFESTEAL,
}

impl StatType {
    fn from_str(s: &str) -> Option<Self> {
        let normalized = s.to_uppercase().replace('-', "_");
        match normalized.as_str() {
            "ATTACK_DAMAGE" | "DAMAGE" | "ATK" => Some(Self::ATTACK_DAMAGE),
            "ATTACK_SPEED" | "SPEED" => Some(Self::ATTACK_SPEED),
            "PROJECTILE_DAMAGE" | "RANGED_DAMAGE" | "ARROW_DAMAGE" => Some(Self::PROJECTILE_DAMAGE),
            "PROJECTILE_SPEED" => Some(Self::PROJECTILE_SPEED),
            "MAGIC_DAMAGE" | "MAGIC" => Some(Self::MAGIC_DAMAGE),
            "MAGIC_AOE_BONUS" | "MAGIC_AOE" => Some(Self::MAGIC_AOE_BONUS),
            "MAGIC_BURST_BONUS" | "MAGIC_BURST" => Some(Self::MAGIC_BURST_BONUS),
            "CRIT_CHANCE" | "CRIT" => Some(Self::CRIT_CHANCE),
            "CRIT_DAMAGE" => Some(Self::CRIT_DAMAGE),
            "MAX_HEALTH" | "HEALTH" | "HP" => Some(Self::MAX_HEALTH),
            "MAX_MANA" | "MANA" => Some(Self::MAX_MANA),
            "DEFENSE" | "DEF" => Some(Self::DEFENSE),
            "MAGIC_RESIST" | "RESIST" => Some(Self::MAGIC_RESIST),
            "MOVE_SPEED" => Some(Self::MOVE_SPEED),
            "LIFESTEAL" => Some(Self::LIFESTEAL),
            _ => None,
        }
    }

    fn display_name(&self) -> &'static str {
        match self {
            Self::ATTACK_DAMAGE => "物理攻撃力",
            Self::ATTACK_SPEED => "攻撃速度",
            Self::PROJECTILE_DAMAGE => "遠距離攻撃力",
            Self::PROJECTILE_SPEED => "弾速",
            Self::MAGIC_DAMAGE => "魔法攻撃力",
            Self::MAGIC_AOE_BONUS => "魔法AoE",
            Self::MAGIC_BURST_BONUS => "魔法バースト",
            Self::CRIT_CHANCE => "会心率",
            Self::CRIT_DAMAGE => "会心ダメ",
            Self::MAX_HEALTH => "最大HP",
            Self::MAX_MANA => "最大マナ",
            Self::DEFENSE => "防御力",
            Self::MAGIC_RESIST => "魔法耐性",
            Self::MOVE_SPEED => "移動速度",
            Self::LIFESTEAL => "ドレイン",
        }
    }
}

#[derive(Debug, Clone)]
struct ItemStats {
    id: String,
    dps: f64,
    stats: HashMap<StatType, f64>,
    item_type_label: String,
}

struct Backend {
    client: Client,
    global_items: DashMap<String, ItemStats>,
    document_map: DashMap<Url, String>,
    stat_distributions: DashMap<StatType, Vec<f64>>,
    dps_distribution: Arc<std::sync::RwLock<Vec<f64>>>,
}

impl Backend {
    fn is_item_file(&self, uri: &Url) -> bool {
        uri.path().contains("items/")
    }

    fn calculate_dps(&self, stats: &HashMap<StatType, f64>) -> (f64, String) {
        let speed = stats.get(&StatType::ATTACK_SPEED).cloned().unwrap_or(1.0);
        let crit_chance = stats.get(&StatType::CRIT_CHANCE).cloned().unwrap_or(0.0).min(100.0) / 100.0;
        let crit_dmg = stats.get(&StatType::CRIT_DAMAGE).cloned().unwrap_or(150.0) / 100.0;
        let crit_factor = 1.0 + crit_chance * (crit_dmg - 1.0);

        let phys_atk = stats.get(&StatType::ATTACK_DAMAGE).cloned().unwrap_or(0.0);
        let proj_atk = stats.get(&StatType::PROJECTILE_DAMAGE).cloned().unwrap_or(0.0);
        let magic_atk = stats.get(&StatType::MAGIC_DAMAGE).cloned().unwrap_or(0.0);

        // 物理DPS: 速度依存
        let phys_dps = if speed > 0.0 { phys_atk * speed * crit_factor } else { 0.0 };
        
        // 遠距離DPS: 引き絞り時間(1.0s)固定として計算
        let proj_dps = proj_atk * 1.0 * crit_factor;
        
        // 魔法DPS: 現状は速度依存
        let magic_dps = if speed > 0.0 { magic_atk * speed * crit_factor } else { 0.0 };

        if proj_dps > phys_dps && proj_dps > magic_dps {
            (proj_dps, "遠距離".to_string())
        } else if magic_dps > phys_dps {
            (magic_dps, "魔法".to_string())
        } else {
            (phys_dps, "物理".to_string())
        }
    }

    fn get_f64_from_value(&self, v: &Value) -> f64 {
        if let Some(f) = v.as_f64() { return f; }
        if let Some(i) = v.as_i64() { return i as f64; }
        if let Some(u) = v.as_u64() { return u as f64; }
        0.0
    }

    fn get_value_from_yaml(&self, value: &Value) -> f64 {
        let direct = self.get_f64_from_value(value);
        if direct != 0.0 { return direct; }
        if let Some(mapping) = value.as_mapping() {
            if let Some(base) = mapping.get(&Value::String("base".to_string())) {
                let base_val = self.get_f64_from_value(base);
                let spread = mapping.get(&Value::String("spread".to_string())).map(|v| self.get_f64_from_value(v)).unwrap_or(0.0);
                return base_val + (spread / 2.0);
            }
            if let Some(flat) = mapping.get(&Value::String("flat".to_string())) {
                return self.get_value_from_yaml(flat);
            }
        }
        0.0
    }

    async fn scan_workspace(&self, root_path: &Path) {
        for entry in WalkDir::new(root_path)
            .into_iter()
            .filter_map(|e| e.ok())
            .filter(|e| e.path().extension().map_or(false, |ext| ext == "yml" || ext == "yaml"))
        {
            if !entry.path().to_string_lossy().contains("items/") { continue; }
            if let Ok(content) = std::fs::read_to_string(entry.path()) {
                self.index_content(&content);
            }
        }
        self.update_distributions();
    }

    fn index_content(&self, content: &str) {
        if let Ok(yaml) = serde_yaml::from_str::<Value>(content) {
            if let Some(mapping) = yaml.as_mapping() {
                for (id_val, data) in mapping {
                    let id = id_val.as_str().unwrap_or("unknown").to_string();
                    let mut item_stats = HashMap::new();
                    if let Some(stats_section) = data.get("stats").and_then(|v| v.as_mapping()) {
                        for (s_key, s_val) in stats_section {
                            if let Some(s_name) = s_key.as_str() {
                                if let Some(st) = StatType::from_str(s_name) {
                                    item_stats.insert(st, self.get_value_from_yaml(s_val));
                                }
                            }
                        }
                    }
                    let (dps, label) = self.calculate_dps(&item_stats);
                    self.global_items.insert(id.clone(), ItemStats { id, dps, stats: item_stats, item_type_label: label });
                }
            }
        }
    }

    fn update_distributions(&self) {
        let mut dists: HashMap<StatType, Vec<f64>> = HashMap::new();
        let mut dps_list = Vec::new();
        for item in self.global_items.iter() {
            dps_list.push(item.value().dps);
            for (st, &val) in &item.value().stats {
                dists.entry(*st).or_default().push(val);
            }
        }
        for (st, mut list) in dists {
            list.sort_by(|a, b| b.partial_cmp(a).unwrap_or(std::cmp::Ordering::Equal));
            self.stat_distributions.insert(st, list);
        }
        dps_list.sort_by(|a, b| b.partial_cmp(a).unwrap_or(std::cmp::Ordering::Equal));
        if let Ok(mut lock) = self.dps_distribution.write() { *lock = dps_list; }
    }

    fn find_item_id_at_line(&self, text: &str, line: usize) -> Option<String> {
        let lines: Vec<&str> = text.lines().collect();
        if line >= lines.len() { return None; }
        for i in (0..=line).rev() {
            let l = lines[i];
            if l.trim().is_empty() || l.starts_with(' ') || l.starts_with('\t') { continue; }
            if let Some(pos) = l.find(':') {
                let key = l[..pos].trim();
                if !key.chars().all(|c| c.is_numeric()) { return Some(key.to_string()); }
            }
        }
        None
    }

    fn get_rank_percentile(list: &[f64], value: f64) -> (usize, usize, f64) {
        if list.is_empty() { return (0, 0, 0.0); }
        let rank = list.iter().filter(|&&x| x > value).count() + 1;
        let total = list.len();
        let percentile = (1.0 - (rank as f64 / total as f64)) * 100.0;
        (rank, total, percentile)
    }
}

#[tower_lsp::async_trait]
impl LanguageServer for Backend {
    async fn initialize(&self, params: InitializeParams) -> Result<InitializeResult> {
        if let Some(root_uri) = params.root_uri {
            if let Ok(root_path) = root_uri.to_file_path() { self.scan_workspace(&root_path).await; }
            else { self.scan_workspace(Path::new(root_uri.path())).await; }
        }
        Ok(InitializeResult {
            server_info: None,
            capabilities: ServerCapabilities {
                text_document_sync: Some(TextDocumentSyncCapability::Kind(TextDocumentSyncKind::FULL)),
                hover_provider: Some(HoverProviderCapability::Simple(true)),
                completion_provider: Some(CompletionOptions {
                    trigger_characters: Some(vec![":".to_string(), " ".to_string()]),
                    ..Default::default()
                }),
                ..Default::default()
            },
        })
    }

    async fn initialized(&self, _: InitializedParams) {
        self.client.log_message(MessageType::INFO, "Deepwither LSP initialized!").await;
    }

    async fn shutdown(&self) -> Result<()> { Ok(()) }

    async fn did_open(&self, params: DidOpenTextDocumentParams) {
        self.document_map.insert(params.text_document.uri.clone(), params.text_document.text.clone());
        self.index_content(&params.text_document.text);
        self.update_distributions();
    }

    async fn did_change(&self, params: DidChangeTextDocumentParams) {
        if let Some(change) = params.content_changes.into_iter().next() {
            self.document_map.insert(params.text_document.uri.clone(), change.text.clone());
            self.index_content(&change.text);
            self.update_distributions();
        }
    }

    async fn hover(&self, params: HoverParams) -> Result<Option<Hover>> {
        let uri = &params.text_document_position_params.text_document.uri;
        if !self.is_item_file(uri) { return Ok(None); }
        let text = match self.document_map.get(uri) { Some(t) => t.value().clone(), None => return Ok(None) };
        let line = params.text_document_position_params.position.line as usize;
        let item_id = match self.find_item_id_at_line(&text, line) { Some(id) => id, None => return Ok(None) };
        let item_stats = match self.global_items.get(&item_id) { Some(s) => s.value().clone(), None => return Ok(None) };

        let mut markdown = format!("### Deepwither Analysis: `{}`\n\n", item_id);
        
        // DPS Ranking
        if let Ok(dist) = self.dps_distribution.read() {
            let (rank, total, pct) = Self::get_rank_percentile(&*dist, item_stats.dps);
            let medal = if pct >= 99.0 { "🥇 " } else if pct >= 95.0 { "🥈 " } else if pct >= 90.0 { "🥉 " } else { "📊 " };
            markdown.push_str(&format!("#### {} 期待{}DPS: `{:.2}`\n", medal, item_stats.item_type_label, item_stats.dps));
            markdown.push_str(&format!("- **ランキング:** `{} / {}` (上位 **{:.1}%**)\n\n", rank, total, 100.0 - pct));
        }

        markdown.push_str("#### 📈 ステータス別ランキング\n");
        let mut stat_keys: Vec<_> = item_stats.stats.keys().collect();
        stat_keys.sort_by_key(|k| k.display_name());

        for st in stat_keys {
            let val = item_stats.stats[st];
            if let Some(dist) = self.stat_distributions.get(st) {
                let (rank, total, pct) = Self::get_rank_percentile(dist.value(), val);
                let indicator = if pct >= 95.0 { "🔥" } else if pct >= 80.0 { "✨" } else { "▫️" };
                markdown.push_str(&format!("- {}: `{:.1}` ({} #{} / {}, 上位 {:.0}%)\n", 
                    st.display_name(), val, indicator, rank, total, 100.0 - pct));
            }
        }

        Ok(Some(Hover {
            contents: HoverContents::Markup(MarkupContent { kind: MarkupKind::Markdown, value: markdown }),
            range: None,
        }))
    }

    async fn completion(&self, params: CompletionParams) -> Result<Option<CompletionResponse>> {
        let uri = &params.text_document_position.text_document.uri;
        if !self.is_item_file(uri) { return Ok(None); }
        let stats = vec![
            "ATTACK_DAMAGE", "ATTACK_SPEED", "CRIT_CHANCE", "CRIT_DAMAGE", 
            "MAGIC_DAMAGE", "MAGIC_AOE_BONUS", "MAGIC_BURST_BONUS",
            "DEFENSE", "MAGIC_RESIST", "MAX_HEALTH", "MAX_MANA", "MOVE_SPEED", "LIFESTEAL"
        ];
        let completions = stats.iter().map(|s| CompletionItem {
            label: s.to_string(),
            kind: Some(CompletionItemKind::PROPERTY),
            detail: Some("Deepwither StatType".to_string()),
            ..Default::default()
        }).collect();
        Ok(Some(CompletionResponse::Array(completions)))
    }
}

#[tokio::main]
async fn main() {
    let stdin = tokio::io::stdin();
    let stdout = tokio::io::stdout();
    let (service, socket) = LspService::new(|client| Backend {
        client,
        global_items: DashMap::new(),
        document_map: DashMap::new(),
        stat_distributions: DashMap::new(),
        dps_distribution: Arc::new(std::sync::RwLock::new(Vec::new())),
    });
    Server::new(stdin, stdout, socket).serve(service).await;
}
