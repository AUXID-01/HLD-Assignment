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

  const debounceTimer = useRef(null)

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
        const response = await fetch(`http://localhost:8080/suggest?q=${encodeURIComponent(query)}`)
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
  }, [query])

  const handleSearch = async (searchQuery) => {
    if (!searchQuery.trim()) return

    try {
      const response = await fetch('http://localhost:8080/search', {
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

  return (
    <div className="container">
      <h1>Search Typeahead</h1>
      
      <div className="search-box">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Search..."
          onFocus={() => setShowDropdown(true)}
          onBlur={() => setTimeout(() => setShowDropdown(false), 200)}
        />
        <button onClick={() => handleSearch(query)}>Search</button>

        {showDropdown && query.trim() !== '' && (
          <ul className="dropdown">
            {loading && <li className="loading">Loading...</li>}
            {error && <li className="error">{error}</li>}
            {!loading && !error && suggestions.length === 0 && (
              <li className="empty">No results found</li>
            )}
            {!loading && !error && suggestions.map((sugg, index) => (
              <li 
                key={sugg.query}
                className={index === highlightedIndex ? 'highlighted' : ''}
                onClick={() => handleSearch(sugg.query)}
                onMouseEnter={() => setHighlightedIndex(index)}
              >
                <span className="sugg-query">{sugg.query}</span>
                <span className="sugg-count">{sugg.count}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {message && <div className="toast">{message}</div>}
    </div>
  )
}

export default App
