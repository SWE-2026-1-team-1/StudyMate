import React, { useState } from "react";

const TAG_COLORS = [
  { bg: "var(--purple)", color: "#fff", border: "none" },
  { bg: "#9bd4fb", color: "#1f5366", border: "none" },
  { bg: "#eef0ff", color: "#5c4cf2", border: "none" },
  { bg: "#e7f7ff", color: "#0394c7", border: "none" },
  { bg: "#fff", color: "#a044dc", border: "1px solid #efd7ff" },
  { bg: "#fff8dc", color: "#db7516", border: "1px solid #f4dfa0" },
  { bg: "#e8fff4", color: "#0aa56d", border: "1px solid #bfead8" },
  { bg: "#f1f3f7", color: "#4d5258", border: "none" },
];

export const TagList = ({ tags, onRemoveTag, onAddTag }: { tags: string[], onRemoveTag?: (tag: string) => void, onAddTag?: (tag: string) => void }) => {
  const [isAdding, setIsAdding] = useState(false);
  const [inputValue, setInputValue] = useState("");

  const handleAdd = () => {
    if (inputValue.trim()) {
      onAddTag?.(inputValue.trim());
      setInputValue("");
    }
    setIsAdding(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleAdd();
    } else if (e.key === "Escape") {
      setIsAdding(false);
      setInputValue("");
    }
  };

  return (
    <div className="keyword-set auth-keywords tag-list-container">
      {tags.length === 0 && (
        <span className="empty-tag-hint">관심 태그를 입력해 주세요</span>
      )}
      {tags.map((tag, index) => {
        const colorStyle = TAG_COLORS[index % TAG_COLORS.length];
        return (
          <span 
            key={tag} 
            style={{ 
              background: colorStyle.bg, 
              color: colorStyle.color,
              border: colorStyle.border !== "none" ? colorStyle.border : undefined,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px'
            }}
          >
            {tag}
            {onRemoveTag && (
              <b style={{ cursor: 'pointer', margin: 0 }} onClick={() => onRemoveTag(tag)}>×</b>
            )}
          </span>
        );
      })}
      
      {onAddTag && (
        isAdding ? (
          <input
            type="text"
            className="add-tag-input"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onBlur={handleAdd}
            onKeyDown={handleKeyDown}
            autoFocus
            placeholder="태그 입력"
          />
        ) : (
          <button type="button" onClick={() => setIsAdding(true)}>+ Add Tag</button>
        )
      )}
    </div>
  );
};
