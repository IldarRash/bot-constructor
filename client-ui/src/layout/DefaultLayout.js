import React from 'react'
import { AppContent, AppSidebar, AppFooter, AppHeader } from '../components/index'
import Login from "../components/pages/login/Login";

const DefaultLayout = (props) => {
  console.log(props);
  const { isAuth } = props;
  if (isAuth) {
    return (
      <div>
        <AppSidebar/>
        <div className="wrapper d-flex flex-column min-vh-100 bg-light">
          <AppHeader/>
          <div className="body flex-grow-1">
            <AppContent/>
          </div>
          <AppFooter/>
        </div>
      </div>
    )
  }
  return (
    <div>
      <div className="wrapper d-flex flex-column min-vh-100 bg-light">
        <AppHeader/>
        <Login {...props}/>
        <AppFooter/>
      </div>
    </div>
  )
}

export default DefaultLayout
