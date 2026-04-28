; 第1階層: アイテムID (ドキュメント直下のキー)
(document
  (block_node
    (block_mapping
      (block_mapping_pair
        key: (_) @type))))

; 第2階層: 基本プロパティ (stats, material, typeなど)
(block_mapping_pair
  value: (block_node
    (block_mapping
      (block_mapping_pair
        key: (_) @property))))

; 第3階層以降: ステータス詳細 (flat, percent, base, spreadなど)
(block_mapping_pair
  value: (block_node
    (block_mapping
      (block_mapping_pair
        value: (block_node
          (block_mapping
            (block_mapping_pair
              key: (_) @variable)))))))

; 値のハイライト
(string_scalar) @string
(integer_scalar) @number
(float_scalar) @number
(boolean_scalar) @constant.builtin.boolean
(comment) @comment
