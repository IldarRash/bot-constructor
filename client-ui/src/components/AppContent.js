import React, { Suspense } from 'react'
import { Redirect, Route, Switch } from 'react-router-dom'
import { CContainer, CSpinner } from '@coreui/react'

// routes config
import routes from '../routes'

const AppContent = () => {
  return (
    <Suspense fallback={<CSpinner color="primary" />}>
      <Switch>
        {routes.map((route, idx) => {
          console.log("Route path",route.path)
          /*if (route.path !== "/bot/board") {
            return (
              route.component && (
                <CContainer lg>
                  <Route
                    key={idx}
                    path={route.path}
                    exact={route.exact}
                    name={route.name}
                    render={(props) => (
                      <>
                        <route.component {...props} />
                      </>
                    )}
                  />
                </CContainer>
              )
            )
          }*/
          return (
            route.component && (
              <Route
                key={idx}
                path={route.path}
                exact={route.exact}
                name={route.name}
                render={(props) => (
                  <>
                    <route.component {...props} />
                  </>
                )}
              />
            )
          )
        })}
        <Redirect from="/" to="/dashboard" />
      </Switch>
    </Suspense>
  )
}

export default React.memo(AppContent)
