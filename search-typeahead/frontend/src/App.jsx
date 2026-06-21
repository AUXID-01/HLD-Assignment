import { useState, useEffect, useRef } from 'react'
import './App.css'

function App() {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [message, setMessage] = useState(null)
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const [showDropdown, setShowDropdown] = useState(false)
  const [mode, setMode] = useState('basic')

  const debounceTimer = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([])
      setShowDropdown(false)
      return
    }

    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current)
    }

    setLoading(true)
    setError(null)

    debounceTimer.current = setTimeout(async () => {
      try {
        const response = await fetch(`http://localhost:8081/suggest?q=${encodeURIComponent(query)}&mode=${mode}`)
        if (!response.ok) {
          throw new Error('Network response was not ok')
        }
        const data = await response.json()
        setSuggestions(data)
        setShowDropdown(true)
        setHighlightedIndex(-1)
      } catch (err) {
        setError('Something went wrong')
        setSuggestions([])
      } finally {
        setLoading(false)
      }
    }, 300)

    return () => clearTimeout(debounceTimer.current)
  }, [query, mode])

  const handleSearch = async (searchQuery) => {
    if (!searchQuery.trim()) return

    try {
      const response = await fetch('http://localhost:8081/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ query: searchQuery }),
      })
      
      if (!response.ok) {
        throw new Error('Search failed')
      }
      
      const data = await response.json()
      setMessage(data.message)
      setQuery(searchQuery)
      setShowDropdown(false)
      inputRef.current?.blur()
      
      setTimeout(() => setMessage(null), 3000)
    } catch (err) {
      setError('Search failed')
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      if (showDropdown && suggestions.length > 0) {
        setHighlightedIndex(prev => Math.min(prev + 1, suggestions.length - 1))
      }
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      if (showDropdown && suggestions.length > 0) {
        setHighlightedIndex(prev => Math.max(prev - 1, -1))
      }
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (highlightedIndex >= 0 && highlightedIndex < suggestions.length) {
        handleSearch(suggestions[highlightedIndex].query)
      } else {
        handleSearch(query)
      }
    } else if (e.key === 'Escape') {
      setShowDropdown(false)
      setHighlightedIndex(-1)
    }
  }

  // Handle clicking outside to close
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (inputRef.current && !inputRef.current.contains(event.target)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [inputRef])


  return (
    <div className="google-container">
      <div className="logo-container">
        <span className="logo-g">G</span>
        <span className="logo-o1">o</span>
        <span className="logo-o2">o</span>
        <span className="logo-g2">g</span>
        <span className="logo-l">l</span>
        <span className="logo-e">e</span>
      </div>
      
      <div className="mode-toggle">
        <button className={mode === 'basic' ? 'active' : ''} onClick={() => setMode('basic')}>Basic Mode</button>
        <button className={mode === 'trending' ? 'active' : ''} onClick={() => setMode('trending')}>Trending Mode</button>
      </div>

      <div className={`search-wrapper ${showDropdown && query.trim() !== '' ? 'dropdown-active' : ''}`} ref={inputRef}>
        <div className="search-input-container">
          <svg className="search-icon" focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path>
          </svg>
          <input
            type="text"
            className="search-input"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => {
              if (query.trim()) setShowDropdown(true)
            }}
            placeholder="Search Google or type a URL"
          />
          <svg className="mic-icon" focusable="false" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="m12 15c1.66 0 3-1.31 3-2.97v-7.02c0-1.66-1.34-3.01-3-3.01s-3 1.34-3 3.01v7.02c0 1.66 1.34 2.97 3 2.97z" fill="#4285f4"></path>
            <path d="m11 18.08h2v3.92h-2z" fill="#34a853"></path>
            <path d="m7.05 16.87c-1.27-1.33-2.05-2.8-2.05-4.67h-2c0 2.61 1.15 4.71 2.87 6.27z" fill="#f4b400"></path>
            <path d="m12 16.93a4.97 5.25 0 0 1 -3.54 -1.55l-1.41 1.49c1.26 1.34 3.02 2.13 4.95 2.13 3.87 0 6.99-2.92 6.99-7h-1.99c0 2.92-2.24 4.93-5 4.93z" fill="#ea4335"></path>
          </svg>
        </div>

        {showDropdown && query.trim() !== '' && (
          <div className="dropdown-content">
            <div className="divider"></div>
            <ul className="suggestions-list">
              {loading && <li className="info-item">Loading...</li>}
              {error && <li className="info-item error">{error}</li>}
              {!loading && !error && suggestions.length === 0 && (
                <li className="info-item">No results found</li>
              )}
              {!loading && !error && suggestions.map((sugg, index) => (
                <li 
                  key={sugg.query}
                  className={`suggestion-item ${index === highlightedIndex ? 'highlighted' : ''}`}
                  onClick={() => handleSearch(sugg.query)}
                  onMouseEnter={() => setHighlightedIndex(index)}
                >
                  <div className="sugg-icon">
                    <svg focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                      <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path>
                    </svg>
                  </div>
                  <div className="sugg-query-text">
                    <span className="sugg-match">{sugg.query.substring(0, query.length)}</span>
                    <span>{sugg.query.substring(query.length)}</span>
                  </div>
                  {sugg.score != null ? (
                    <span className="sugg-score">⭐ {sugg.score.toFixed(2)}</span>
                  ) : null}
                  <span className="sugg-count">({sugg.count})</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="action-buttons">
        <button onClick={() => handleSearch(query)}>Google Search</button>
        <button onClick={() => handleSearch(query)}>I'm Feeling Lucky</button>
      </div>

      {message && <div className="toast">{message}</div>}
    </div>
  )
}

export default App
