import React from 'react'
import { CFooter } from '@coreui/react'

const AppFooter = () => {
  return (
    <CFooter>
      <div>
        <a href="https://coreui.io" target="_blank" rel="noopener noreferrer">
          Шаяхметов Ильдар
        </a>
        <span className="ms-1">&copy; 2021 creativeLabs.</span>
      </div>
      <div className="ms-auto">
        <span className="me-1">Сделано с любовью</span>
      </div>
    </CFooter>
  )
}

export default React.memo(AppFooter)
